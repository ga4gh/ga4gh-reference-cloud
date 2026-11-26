// Workflow runs as WES lists them (the researcher's own, per compute platform; spec 0014), merged into one list
// across platforms, newest first, and how the UI shows their states and times.

// a run in WES GET /runs. workflow_url and submitted_time are the refcloud's additions to WES's run summary:
// submitted_time is what each platform's list is ordered by (newest first), so it's also what merges them
export type RunSummary = {
  run_id: string;
  state: string;
  start_time?: string;
  end_time?: string;
  tags: Record<string, string>;
  workflow_url?: string;
  submitted_time?: string;
};

// WES GET /runs; next_page_token is "" on the last page
export type RunListResponse = {
  runs: RunSummary[];
  next_page_token: string;
};

// a run in the merged list: which platform it's on
export type PlatformRun = RunSummary & { platformId: string };

// what the page has loaded from one platform so far
export type PlatformRuns = {
  runs: RunSummary[];
  // "" once the platform's last page is loaded
  nextPageToken: string;
  // set when the platform didn't answer, or refused (its status)
  error?: string;
};

// the states a run ends in; any other state (QUEUED, INITIALIZING, RUNNING, CANCELING) means it isn't finished yet
export const FINISHED_STATES = new Set(["COMPLETE", "EXECUTOR_ERROR", "SYSTEM_ERROR", "CANCELED", "PREEMPTED"]);

export function isFinished(state: string): boolean {
  return FINISHED_STATES.has(state);
}

// each state's label, and its pill (the access statuses' classes, styles/globals.css): in progress amber, done green,
// failed red, cancelled or unknown grey
const STATES: Record<string, { label: string; pill: string }> = {
  QUEUED: { label: "Queued", pill: "ga4gh-status--requested" },
  INITIALIZING: { label: "Initializing", pill: "ga4gh-status--requested" },
  RUNNING: { label: "Running", pill: "ga4gh-status--requested" },
  PAUSED: { label: "Paused", pill: "ga4gh-status--requested" },
  CANCELING: { label: "Cancelling", pill: "ga4gh-status--not-requested" },
  CANCELED: { label: "Cancelled", pill: "ga4gh-status--not-requested" },
  COMPLETE: { label: "Complete", pill: "ga4gh-status--approved" },
  EXECUTOR_ERROR: { label: "Executor error", pill: "ga4gh-status--denied" },
  SYSTEM_ERROR: { label: "System error", pill: "ga4gh-status--denied" },
  PREEMPTED: { label: "Preempted", pill: "ga4gh-status--denied" },
};

export function stateLabel(state: string): string {
  return STATES[state]?.label ?? state;
}

export function statePillClass(state: string): string {
  return `ga4gh-status ${STATES[state]?.pill ?? "ga4gh-status--not-requested"}`;
}

// a run's submission time in milliseconds (0 when it's missing), compared as a number: timestamps' fractional seconds
// vary in length, so their strings don't sort
function submitted(run: RunSummary): number {
  return run.submitted_time ? Date.parse(run.submitted_time) || 0 : 0;
}

// newest first, by submission time, then by run ID, as each platform orders its own list
function newestFirst(a: PlatformRun, b: PlatformRun): number {
  const byTime = submitted(b) - submitted(a);
  return byTime !== 0 ? byTime : b.run_id.localeCompare(a.run_id);
}

// the runs to show, merged from every platform, newest first. While a platform has more pages, its unloaded runs are
// all older than the oldest it has sent, so only runs at least that new are shown: the newest such limit among the
// platforms with more. "Load more" then never adds a run above one already shown
export function mergeRuns(byPlatform: Record<string, PlatformRuns>): { runs: PlatformRun[]; hasMore: boolean } {
  const all: PlatformRun[] = [];
  let limit: number | null = null;
  for (const [platformId, loaded] of Object.entries(byPlatform)) {
    for (const run of loaded.runs) {
      all.push({ ...run, platformId });
    }
    if (loaded.nextPageToken && !loaded.error && loaded.runs.length > 0) {
      const oldest = submitted(loaded.runs[loaded.runs.length - 1]);
      if (limit === null || oldest > limit) {
        limit = oldest;
      }
    }
  }
  all.sort(newestFirst);
  const runs = limit === null ? all : all.filter((run) => submitted(run) >= (limit as number));
  return { runs, hasMore: limit !== null };
}

// a run ID's first part, to fit a table; the run's page shows it whole
export function shortRunId(runId: string): string {
  return runId.split("-")[0];
}

// a date and time in the browser's locale and time zone
export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

// a duration as hours, minutes and seconds: 1h 2m, 3m 12s, 45s
export function formatDuration(from: string, to: Date): string {
  const seconds = Math.max(0, Math.round((to.getTime() - new Date(from).getTime()) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return minutes > 0 ? `${minutes}m ${seconds % 60}s` : `${seconds}s`;
}
