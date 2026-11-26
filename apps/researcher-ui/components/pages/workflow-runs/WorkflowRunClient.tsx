"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import AppLayout from "@/components/layout/AppLayout";
import { usePassportToken } from "@/components/context/PassportTokenContext";
import { ComputePlatform } from "@/components/pages/compute-platforms/ComputePlatformsClient";
import CopyableValue from "@/components/pages/compute-platforms/CopyableValue";
import CancelRunButton from "@/components/pages/workflow-runs/CancelRunButton";
import RunLogsSection from "@/components/pages/workflow-runs/RunLogsSection";
import RunTasksSection from "@/components/pages/workflow-runs/RunTasksSection";
import {
  formatDuration,
  formatSize,
  formatTime,
  isCancellable,
  isFinished,
  outputDownloadPath,
  RunLog,
  stateLabel,
  statePillClass,
} from "@/utils/workflowRuns";

// how often the run's state is checked while it isn't finished
const REFRESH_MILLISECONDS = 5_000;

interface WorkflowRunClientProps {
  platformId: string;
  runId: string;
  // the compute platforms, for the platform's and workflow's names; null when the server couldn't fetch them
  platforms: ComputePlatform[] | null;
}

type LoadError = { status: number | null };

// the run log (WES GET /runs/{run_id}) through the WES proxy, with the researcher's passport
async function fetchRunLog(runPath: string): Promise<RunLog | LoadError> {
  try {
    const response = await fetch(runPath);
    return response.ok ? ((await response.json()) as RunLog) : { status: response.status };
  } catch {
    return { status: null };
  }
}

function loadErrorMessage(error: LoadError, platformName: string): string {
  switch (error.status) {
    case 404:
      return `No such run on ${platformName}: it may not exist, or it may not be one of yours.`;
    case 401:
      return "Your passport couldn't be verified. Reload the page to get a new one.";
    case 403:
      return `You don't have access to ${platformName} any more, so its runs can't be shown.`;
    case null:
      return `${platformName} couldn't be reached. Try again later.`;
    default:
      return `Could not load the run: ${platformName} answered ${error.status}.`;
  }
}

// a label and its value in the run's overview
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="ga4gh-cta">{label}</div>
      <div>{children}</div>
    </div>
  );
}

// one of the researcher's workflow runs, in sections: its overview (with cancelling), its outputs, its tasks and its
// logs. The page follows the run while it isn't finished
export default function WorkflowRunClient({ platformId, runId, platforms }: WorkflowRunClientProps) {
  const { isTokenLoaded } = usePassportToken();
  const runPath = `/api/ga4gh/wes/${encodeURIComponent(platformId)}/v1/runs/${encodeURIComponent(runId)}`;
  const [run, setRun] = useState<RunLog | null>(null);
  const [loadError, setLoadError] = useState<LoadError | null>(null);
  const [now, setNow] = useState(() => new Date());

  const platform = platforms?.find((entry) => entry.id === platformId);
  const platformName = platform?.name ?? platformId;

  // a fetched run log, or why it couldn't be, into the page
  const applyRunLog = useCallback((result: RunLog | LoadError) => {
    if ("run_id" in result) {
      setRun(result);
      setLoadError(null);
    } else {
      setLoadError(result);
    }
    setNow(new Date());
  }, []);

  const reload = useCallback(async () => applyRunLog(await fetchRunLog(runPath)), [applyRunLog, runPath]);

  // the run log, once the passport is in place
  useEffect(() => {
    if (!isTokenLoaded) {
      return;
    }
    let current = true;
    fetchRunLog(runPath).then((result) => {
      if (current) {
        applyRunLog(result);
      }
    });
    return () => {
      current = false;
    };
  }, [isTokenLoaded, runPath, applyRunLog]);

  // while the run isn't finished: its state every 5 seconds, and the whole run log again when the state changes
  const state = run?.state;
  useEffect(() => {
    if (!state || isFinished(state)) {
      return;
    }
    const timer = setInterval(async () => {
      try {
        const response = await fetch(`${runPath}/status`);
        if (response.ok) {
          const status = (await response.json()) as { state: string };
          if (status.state !== state) {
            await reload();
            return;
          }
        }
      } catch {
        // the next check tries again
      }
      setNow(new Date());
    }, REFRESH_MILLISECONDS);
    return () => clearInterval(timer);
  }, [state, runPath, reload]);

  const request = run?.request;
  const workflowUrl = request?.workflow_url;
  const workflow = platform?.workflows.find((entry) => entry.workflowUrl === workflowUrl);
  const startTime = run?.run_log?.start_time;
  const endTime = run?.run_log?.end_time;
  const outputs = run?.outputs?.files ?? [];
  const systemLogs = run?.run_log?.system_logs ?? [];
  const tags = Object.entries(request?.tags ?? {});
  const params = request?.workflow_params;
  const hasParams = params !== undefined && params !== null && Object.keys(params as object).length > 0;

  return (
    <AppLayout>
      <Link className="link link-primary" href="/workflow-runs">
        ← All workflow runs
      </Link>
      <h1 className="mt-4 mb-6 text-5xl font-bold">Workflow Run</h1>

      {loadError && run === null ? (
        <div role="alert" className="alert alert-error mb-4 rounded-none">
          <span>{loadErrorMessage(loadError, platformName)}</span>
        </div>
      ) : run === null ? (
        <p>Loading the run…</p>
      ) : (
        <>
          {loadError ? (
            <div role="alert" className="alert alert-warning mb-4 rounded-none">
              <span>Could not refresh the run: {loadErrorMessage(loadError, platformName)}</span>
            </div>
          ) : null}

          <section className="mb-8">
            <div className="card bg-base-100 w-full box-shadow-card">
              <div className="card-body gap-4">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <h2 className="card-title text-2xl break-all">{workflow?.name ?? workflowUrl ?? "Workflow run"}</h2>
                  {isCancellable(run.state) ? <CancelRunButton runPath={runPath} onCancelled={() => reload()} /> : null}
                </div>

                <div className="card-actions items-center">
                  <div className="ga4gh-cta">State:</div>
                  <span className={statePillClass(run.state)}>{stateLabel(run.state)}</span>
                  {run.run_log?.exit_code !== undefined && run.run_log?.exit_code !== null ? (
                    <span className="text-sm opacity-70">workflow engine exit code {run.run_log.exit_code}</span>
                  ) : null}
                </div>

                {systemLogs.length > 0 ? (
                  <Field label="Reason">
                    <ul>
                      {systemLogs.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  </Field>
                ) : null}

                <div className="grid gap-4 md:grid-cols-2">
                  <Field label="Run ID">
                    <CopyableValue value={run.run_id} label="run ID" />
                  </Field>
                  <Field label="Platform">
                    {platformName}
                    {platform ? null : (
                      <span className="text-sm opacity-70"> (not listed on Browse Compute Platforms)</span>
                    )}
                  </Field>
                  <Field label="Submitted">{run.submitted_time ? formatTime(run.submitted_time) : "—"}</Field>
                  <Field label="Started">{startTime ? formatTime(startTime) : "Not yet"}</Field>
                  <Field label="Ended">{endTime ? formatTime(endTime) : isFinished(run.state) ? "—" : "Not yet"}</Field>
                  <Field label="Duration">
                    {startTime
                      ? endTime
                        ? formatDuration(startTime, new Date(endTime))
                        : `${formatDuration(startTime, now)} so far`
                      : "—"}
                  </Field>
                </div>

                {workflowUrl ? (
                  <Field label="Workflow URL">
                    <CopyableValue value={workflowUrl} label="workflow_url" />
                    {request?.workflow_type ? (
                      <div className="text-sm opacity-70">
                        workflow_type {request.workflow_type}, workflow_type_version {request.workflow_type_version}
                      </div>
                    ) : null}
                  </Field>
                ) : null}

                <Field label="Parameters">
                  {hasParams ? (
                    <pre className="p-2 bg-base-300 text-base-content text-xs max-h-80 overflow-auto rounded-none">
                      <code>{JSON.stringify(params, null, 2)}</code>
                    </pre>
                  ) : (
                    <span className="text-sm">None: the workflow&apos;s defaults</span>
                  )}
                </Field>

                {tags.length > 0 ? (
                  <Field label="Tags">
                    <ul className="text-sm">
                      {tags.map(([key, value]) => (
                        <li key={key}>
                          <span className="font-mono">{key}</span>: {value}
                        </li>
                      ))}
                    </ul>
                  </Field>
                ) : null}
              </div>
            </div>
          </section>

          <section className="mb-8">
            <h2 className="mb-2 text-2xl">Outputs</h2>
            <p className="mb-4">The files the workflow published to the run&apos;s outputs.</p>
            {outputs.length === 0 ? (
              <p className="p-4 bg-base-200 rounded-none">
                {isFinished(run.state)
                  ? "This run has no outputs."
                  : "No outputs yet: they're listed here when the run ends."}
              </p>
            ) : (
              <div className="w-full overflow-x-auto border border-base-300 rounded-none">
                <table className="ga4gh-data-table table table-zebra table-sm w-full rounded-none">
                  <thead>
                    <tr>
                      <th className="bg-base-200 text-base-content">File</th>
                      <th className="bg-base-200 text-base-content">Size</th>
                      <th className="bg-base-200 text-base-content">
                        <span className="sr-only">Download</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {outputs.map((file) => (
                      <tr key={file.path}>
                        <td data-label="File" className="font-mono break-all">
                          {file.path}
                        </td>
                        <td data-label="Size" className="whitespace-nowrap">
                          {formatSize(file.size)}
                        </td>
                        <td data-label="Download">
                          <a
                            className="link link-primary"
                            href={outputDownloadPath(platformId, run.run_id, file.path)}
                            download
                          >
                            Download
                          </a>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <RunTasksSection platformId={platformId} runPath={runPath} runState={run.state} now={now} />

          <RunLogsSection runPath={runPath} runState={run.state} />
        </>
      )}
    </AppLayout>
  );
}
