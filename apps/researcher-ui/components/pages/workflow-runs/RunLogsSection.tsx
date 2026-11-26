"use client";

import { useEffect, useRef, useState } from "react";
import { lastLines } from "@/utils/workflowRuns";

// how much of a log the page shows; the whole file is a download
export const LOG_LINES_SHOWN = 500;

type LogStream = "stdout" | "stderr";

interface RunLogsSectionProps {
  // the run's WES proxy path: /api/ga4gh/wes/{platformId}/v1/runs/{runId}
  runPath: string;
  // the run's state: a log is fetched again when it changes (e.g. when the run ends), and otherwise on request
  runState: string;
}

// a loaded log says which stream it is, so a late answer for the other tab is never shown
type LoadedLog = { stream: LogStream } & ({ text: string } | { error: string });

async function fetchLog(runPath: string, stream: LogStream): Promise<LoadedLog> {
  try {
    const response = await fetch(`${runPath}/logs/${stream}`);
    return response.ok
      ? { stream, text: await response.text() }
      : { stream, error: `the platform answered ${response.status}` };
  } catch {
    return { stream, error: "the platform couldn't be reached" };
  }
}

// the workflow engine's console output (the refcloud's WES log endpoint): the last 500 lines of stdout or stderr,
// with a download of the whole file
export default function RunLogsSection({ runPath, runState }: RunLogsSectionProps) {
  const [stream, setStream] = useState<LogStream>("stdout");
  const [loadedLog, setLoadedLog] = useState<LoadedLog | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const logRef = useRef<HTMLPreElement>(null);

  // the chosen stream, again whenever the run's state changes (e.g. when it ends)
  useEffect(() => {
    let current = true;
    fetchLog(runPath, stream).then((loaded) => {
      if (current) {
        setLoadedLog(loaded);
      }
    });
    return () => {
      current = false;
    };
  }, [runPath, stream, runState]);

  const refresh = async () => {
    setIsRefreshing(true);
    setLoadedLog(await fetchLog(runPath, stream));
    setIsRefreshing(false);
  };

  // until the chosen stream's log is in, the section shows it loading
  const log = loadedLog?.stream === stream ? loadedLog : null;
  const shown = log && "text" in log ? lastLines(log.text, LOG_LINES_SHOWN) : null;

  // a log is read from its end, so the view starts there
  useEffect(() => {
    if (logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight;
    }
  }, [log]);

  return (
    <section className="mb-8">
      <h2 className="mb-2 text-2xl">Logs</h2>
      <p className="mb-4">
        The workflow engine&apos;s console output for this run. Each task&apos;s own output is under Tasks.
      </p>

      <div className="flex flex-wrap items-center gap-4 mb-2">
        <div role="tablist" className="tabs tabs-border">
          {(["stdout", "stderr"] as const).map((name) => (
            <button
              key={name}
              type="button"
              role="tab"
              aria-selected={stream === name}
              className={`tab font-mono ${stream === name ? "tab-active" : ""}`}
              onClick={() => setStream(name)}
            >
              {name}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="btn btn-sm rounded-none"
          onClick={refresh}
          disabled={isRefreshing || log === null}
        >
          {isRefreshing ? "Refreshing…" : "Refresh"}
        </button>
        <a
          className="btn btn-sm rounded-none"
          href={`${runPath}/logs/${stream}?download=1`}
          download
          title={`Download the whole ${stream} log`}
        >
          Download the whole log
        </a>
      </div>

      {log === null ? (
        <p>Loading the log…</p>
      ) : "error" in log ? (
        <div role="alert" className="alert alert-warning rounded-none">
          <span>
            Could not load {stream}: {log.error}.
          </span>
        </div>
      ) : shown === null || shown.total === 0 ? (
        <p className="p-4 bg-base-200 rounded-none">Nothing has been written to {stream} yet.</p>
      ) : (
        <>
          <p className="text-sm mb-1" role="status">
            {shown.truncated ? (
              <strong>
                Showing only the last {LOG_LINES_SHOWN.toLocaleString()} of {shown.total.toLocaleString()} lines.
                Download the whole log to see the rest.
              </strong>
            ) : (
              `All ${shown.total.toLocaleString()} ${shown.total === 1 ? "line" : "lines"}.`
            )}
          </p>
          <pre
            ref={logRef}
            className="p-2 bg-base-300 text-base-content text-xs max-h-[60vh] overflow-auto rounded-none whitespace-pre-wrap break-all"
          >
            <code>{shown.shown}</code>
          </pre>
        </>
      )}
    </section>
  );
}
