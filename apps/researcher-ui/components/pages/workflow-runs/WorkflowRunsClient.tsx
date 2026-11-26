"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import AppLayout from "@/components/layout/AppLayout";
import { useEnv } from "@/components/context/EnvContext";
import { usePassportToken } from "@/components/context/PassportTokenContext";
import { ComputePlatform } from "@/components/pages/compute-platforms/ComputePlatformsClient";
import { assertionStatus, PassportVisaAssertionStatus } from "@/utils/passportVisa";
import {
  formatDuration,
  formatTime,
  isFinished,
  mergeRuns,
  PlatformRun,
  PlatformRuns,
  RunListResponse,
  RunSummary,
  shortRunId,
  stateLabel,
  statePillClass,
} from "@/utils/workflowRuns";

// each platform's runs come a page at a time
const PAGE_SIZE = 20;

// how often the list refreshes while any run on it isn't finished
const REFRESH_MILLISECONDS = 10_000;

interface WorkflowRunsClientProps {
  // null when the server-side fetch failed
  initialPlatforms: ComputePlatform[] | null;
}

// one page of a platform's runs, through the WES proxy with the researcher's passport. A platform that doesn't
// answer, or refuses, comes back with an error instead
async function fetchRunsPage(platformId: string, pageToken: string | null): Promise<PlatformRuns> {
  const params = new URLSearchParams({ page_size: String(PAGE_SIZE) });
  if (pageToken) {
    params.set("page_token", pageToken);
  }
  try {
    const response = await fetch(`/api/ga4gh/wes/${encodeURIComponent(platformId)}/v1/runs?${params}`);
    if (!response.ok) {
      return { runs: [], nextPageToken: "", error: `it answered ${response.status}` };
    }
    const page = (await response.json()) as RunListResponse;
    return { runs: page.runs ?? [], nextPageToken: page.next_page_token ?? "" };
  } catch {
    return { runs: [], nextPageToken: "", error: "it couldn't be reached" };
  }
}

// a platform's newest page folded into what's already loaded: updated runs replace their old copies, new ones join
// them. The paging position is kept, since WES's page tokens point at a run, not an offset
function withRefreshedPage(loaded: PlatformRuns, page: PlatformRuns): PlatformRuns {
  if (page.error) {
    return loaded;
  }
  // a platform that hadn't answered starts over from this page, with its paging position
  if (loaded.error) {
    return page;
  }
  const updated = new Map(page.runs.map((run) => [run.run_id, run]));
  const kept = loaded.runs.map((run) => updated.get(run.run_id) ?? run);
  const known = new Set(loaded.runs.map((run) => run.run_id));
  const added = page.runs.filter((run) => !known.has(run.run_id));
  return { ...loaded, runs: [...added, ...kept], error: undefined };
}

export default function WorkflowRunsClient({ initialPlatforms }: WorkflowRunsClientProps) {
  const env = useEnv();
  const { isTokenLoaded } = usePassportToken();
  const [platforms, setPlatforms] = useState<ComputePlatform[] | null>(initialPlatforms);
  const [platformsError, setPlatformsError] = useState(false);
  // per platform, what's loaded so far; null until the first pages are in
  const [byPlatform, setByPlatform] = useState<Record<string, PlatformRuns> | null>(null);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [now, setNow] = useState(() => new Date());

  // the platforms, when the server couldn't fetch them
  useEffect(() => {
    if (initialPlatforms !== null) {
      return;
    }
    fetch("/api/compute-platforms")
      .then((response) => (response.ok ? response.json() : Promise.reject(new Error(`${response.status}`))))
      .then((result) => setPlatforms(Array.isArray(result) ? (result as ComputePlatform[]) : []))
      .catch((error) => {
        console.error("Failed to fetch compute platforms", error);
        setPlatformsError(true);
      });
  }, [initialPlatforms]);

  // only the platforms the researcher has access to: WES refuses the rest
  const approved = useMemo(
    () =>
      (platforms ?? []).filter((platform) => assertionStatus(platform.visa) === PassportVisaAssertionStatus.Approved),
    [platforms],
  );

  // each platform's first page, once the passport is in place
  useEffect(() => {
    if (!isTokenLoaded || platforms === null) {
      return;
    }
    let cancelled = false;
    Promise.all(approved.map(async (platform) => [platform.id, await fetchRunsPage(platform.id, null)] as const)).then(
      (pages) => {
        if (!cancelled) {
          setByPlatform(Object.fromEntries(pages));
          setNow(new Date());
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [isTokenLoaded, platforms, approved]);

  const { runs, hasMore } = useMemo(
    () => (byPlatform === null ? { runs: [], hasMore: false } : mergeRuns(byPlatform)),
    [byPlatform],
  );
  const hasActiveRuns = runs.some((run) => !isFinished(run.state));

  // while any listed run isn't finished: each platform's newest page again, every 10 seconds
  useEffect(() => {
    if (!hasActiveRuns) {
      return;
    }
    const timer = setInterval(async () => {
      const pages = await Promise.all(
        approved.map(async (platform) => [platform.id, await fetchRunsPage(platform.id, null)] as const),
      );
      setByPlatform((current) => {
        if (current === null) {
          return current;
        }
        const next = { ...current };
        for (const [platformId, page] of pages) {
          next[platformId] = next[platformId] ? withRefreshedPage(next[platformId], page) : page;
        }
        return next;
      });
      setNow(new Date());
    }, REFRESH_MILLISECONDS);
    return () => clearInterval(timer);
  }, [hasActiveRuns, approved]);

  // the next page from every platform that has more
  const loadMore = useCallback(async () => {
    if (byPlatform === null) {
      return;
    }
    setIsLoadingMore(true);
    const pending = Object.entries(byPlatform).filter(([, loaded]) => loaded.nextPageToken && !loaded.error);
    const pages = await Promise.all(
      pending.map(
        async ([platformId, loaded]) => [platformId, await fetchRunsPage(platformId, loaded.nextPageToken)] as const,
      ),
    );
    setByPlatform((current) => {
      if (current === null) {
        return current;
      }
      const next = { ...current };
      for (const [platformId, page] of pages) {
        const loaded = next[platformId];
        next[platformId] = page.error
          ? { ...loaded, error: page.error }
          : { runs: [...loaded.runs, ...page.runs], nextPageToken: page.nextPageToken };
      }
      return next;
    });
    setIsLoadingMore(false);
  }, [byPlatform]);

  const platformsById = useMemo(() => new Map(approved.map((platform) => [platform.id, platform])), [approved]);
  const failedPlatforms = byPlatform === null ? [] : approved.filter((platform) => byPlatform[platform.id]?.error);

  // the workflow's name on its platform's allowlist, or its URL
  const workflowName = (run: PlatformRun): string => {
    const workflow = platformsById
      .get(run.platformId)
      ?.workflows.find((entry) => entry.workflowUrl === run.workflow_url);
    return workflow?.name ?? run.workflow_url ?? "";
  };

  const timing = (run: RunSummary) => {
    if (!run.start_time) {
      return run.submitted_time ? <span>Submitted {formatTime(run.submitted_time)}</span> : null;
    }
    return (
      <>
        <div>Started {formatTime(run.start_time)}</div>
        <div className="opacity-70">
          {run.end_time
            ? `Ended ${formatTime(run.end_time)} (${formatDuration(run.start_time, new Date(run.end_time))})`
            : `Running for ${formatDuration(run.start_time, now)}`}
        </div>
      </>
    );
  };

  const isLoading = !platformsError && (platforms === null || (approved.length > 0 && byPlatform === null));

  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Workflow Runs</h1>
      <h2 className="mb-4 text-2xl">
        The runs you submitted through the WES API of each compute platform you have access to
      </h2>

      {platformsError ? (
        <div role="alert" className="alert alert-error mb-4 rounded-none">
          <span>Could not load the compute platforms. Try again later.</span>
        </div>
      ) : null}

      {failedPlatforms.map((platform) => (
        <div key={platform.id} role="alert" className="alert alert-warning mb-4 rounded-none">
          <span>
            Could not load your runs on {platform.name}: {byPlatform?.[platform.id]?.error}. Runs on other platforms are
            listed below.
          </span>
        </div>
      ))}

      {isLoading ? (
        <p>Loading your workflow runs…</p>
      ) : platforms !== null && approved.length === 0 ? (
        <div className="p-4 bg-base-200 text-base-content rounded-none">
          You don&apos;t have access to a compute platform yet.{" "}
          <Link className="link link-primary" href="/compute-platforms">
            Request access on Browse Compute Platforms
          </Link>
          .
        </div>
      ) : byPlatform !== null && runs.length === 0 && failedPlatforms.length < approved.length ? (
        <div className="p-4 bg-base-200 text-base-content rounded-none">
          You haven&apos;t submitted any workflow runs yet. Runs are submitted through each platform&apos;s WES API:{" "}
          <a
            className="link link-primary"
            href={`${env.REFCLOUD_DOCS_URL}/docs/interactive-demos/run-a-workflow-via-wes`}
            target="_blank"
            rel="noreferrer"
          >
            Run a Workflow Via WES
          </a>{" "}
          shows how.
        </div>
      ) : runs.length > 0 ? (
        <>
          <div className="w-full overflow-x-auto border border-base-300 rounded-none">
            <table className="ga4gh-data-table table table-zebra table-sm w-full rounded-none">
              <thead>
                <tr>
                  <th className="bg-base-200 text-base-content">Run</th>
                  <th className="bg-base-200 text-base-content">Workflow</th>
                  <th className="bg-base-200 text-base-content">Platform</th>
                  <th className="bg-base-200 text-base-content">State</th>
                  <th className="bg-base-200 text-base-content">Time</th>
                </tr>
              </thead>
              <tbody>
                {runs.map((run) => (
                  <tr key={`${run.platformId}/${run.run_id}`}>
                    <td data-label="Run">
                      <Link
                        className="link link-primary font-mono"
                        href={`/workflow-runs/${encodeURIComponent(run.platformId)}/${encodeURIComponent(run.run_id)}`}
                        title={run.run_id}
                      >
                        {shortRunId(run.run_id)}
                      </Link>
                    </td>
                    <td data-label="Workflow" className="break-all">
                      {workflowName(run)}
                    </td>
                    <td data-label="Platform">{platformsById.get(run.platformId)?.name ?? run.platformId}</td>
                    <td data-label="State">
                      <span className={statePillClass(run.state)}>{stateLabel(run.state)}</span>
                    </td>
                    <td data-label="Time" className="whitespace-nowrap">
                      {timing(run)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {hasMore ? (
            <button
              type="button"
              className="ga4gh-btn-dark aspect-auto mt-4"
              onClick={loadMore}
              disabled={isLoadingMore}
            >
              <span className="btn-text">{isLoadingMore ? "Loading…" : "Load more"}</span>
            </button>
          ) : null}
        </>
      ) : null}
    </AppLayout>
  );
}
