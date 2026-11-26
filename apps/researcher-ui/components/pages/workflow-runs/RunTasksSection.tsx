"use client";

import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import CopyableValue from "@/components/pages/compute-platforms/CopyableValue";
import { LOG_LINES_SHOWN } from "@/components/pages/workflow-runs/RunLogsSection";
import {
  formatDuration,
  formatTime,
  isFinished,
  lastLines,
  stateLabel,
  statePillClass,
  TaskListResponse,
  TaskLog,
  TesTask,
} from "@/utils/workflowRuns";

// a run's tasks come a page at a time, newest first
const PAGE_SIZE = 50;

// how often the tasks refresh while the run, or any task shown, isn't finished
const REFRESH_MILLISECONDS = 5_000;

interface RunTasksSectionProps {
  platformId: string;
  // the run's WES proxy path: /api/ga4gh/wes/{platformId}/v1/runs/{runId}
  runPath: string;
  runState: string;
  // the time "Running for" counts to, from the page's refresh
  now: Date;
}

type LoadedTasks = { tasks: TaskLog[]; nextPageToken: string };

// what a refresh found: the newest page and TES's copy of each unfinished task (whether it's opened, and its view;
// null when TES didn't answer), or why the page couldn't be loaded
type Refreshed = { page: LoadedTasks; fetched: (readonly [string, boolean, TesTask | null])[] } | { error: string };

async function fetchTasksPage(runPath: string, pageToken: string): Promise<LoadedTasks> {
  const params = new URLSearchParams({ page_size: String(PAGE_SIZE) });
  if (pageToken) {
    params.set("page_token", pageToken);
  }
  const response = await fetch(`${runPath}/tasks?${params}`);
  if (!response.ok) {
    throw new Error(`the platform answered ${response.status}`);
  }
  const page = (await response.json()) as TaskListResponse;
  return { tasks: page.task_logs ?? [], nextPageToken: page.next_page_token ?? "" };
}

// a task as TES has it: MINIMAL is only its state, FULL adds its executors' stdout and stderr
async function fetchTesTask(platformId: string, taskId: string, view: "MINIMAL" | "FULL"): Promise<TesTask | null> {
  try {
    const response = await fetch(
      `/api/ga4gh/tes/${encodeURIComponent(platformId)}/v1/tasks/${encodeURIComponent(taskId)}?view=${view}`,
    );
    return response.ok ? ((await response.json()) as TesTask) : null;
  } catch {
    return null;
  }
}

// the newest page folded into what's loaded: updated tasks replace their old copies, new ones join at the top, and
// the paging position is kept
function withRefreshedPage(loaded: LoadedTasks, page: LoadedTasks): LoadedTasks {
  const updated = new Map(page.tasks.map((task) => [task.id, task]));
  const known = new Set(loaded.tasks.map((task) => task.id));
  return {
    tasks: [
      ...page.tasks.filter((task) => !known.has(task.id)),
      ...loaded.tasks.map((task) => updated.get(task.id) ?? task),
    ],
    nextPageToken: loaded.nextPageToken,
  };
}

// one executor's stdout or stderr, as TES keeps it (only its end). Like the run's logs, the view starts at its end
function ExecutorOutput({ name, text }: { name: string; text?: string }) {
  const outputRef = useRef<HTMLPreElement>(null);
  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight;
    }
  }, [text]);

  if (!text) {
    return <div className="text-sm opacity-70">Nothing in {name}.</div>;
  }
  const shown = lastLines(text, LOG_LINES_SHOWN);
  return (
    <div className="mb-2">
      <div className="text-sm font-mono font-bold">
        {name}
        {shown.truncated ? (
          <span className="font-sans font-normal">
            {" "}
            (showing only the last {LOG_LINES_SHOWN.toLocaleString()} of {shown.total.toLocaleString()} lines)
          </span>
        ) : null}
      </div>
      <pre
        ref={outputRef}
        className="p-2 bg-base-300 text-base-content text-xs max-h-80 overflow-auto rounded-none whitespace-pre-wrap break-all"
      >
        <code>{shown.shown}</code>
      </pre>
    </div>
  );
}

// a task's details: its TES ID and URL, and each executor's exit code, stdout and stderr
function TaskDetails({ task, tesTask }: { task: TaskLog; tesTask: TesTask | null | undefined }) {
  const attempts = tesTask?.logs ?? [];
  return (
    <div className="flex flex-col gap-2 py-2">
      <div>
        <div className="ga4gh-cta">TES task ID</div>
        <CopyableValue value={task.id} label="TES task ID" />
      </div>
      {task.tes_uri ? (
        <div>
          <div className="ga4gh-cta">TES URL</div>
          <CopyableValue value={task.tes_uri} label="TES URL" />
        </div>
      ) : null}
      <div className="ga4gh-cta">Executor output</div>
      <p className="text-sm">
        TES keeps the end of each executor&apos;s output. The task&apos;s files are in the run&apos;s outputs when its
        workflow publishes them.
      </p>
      {tesTask === undefined ? (
        <p className="text-sm">Loading the task&apos;s output…</p>
      ) : tesTask === null ? (
        <div role="alert" className="alert alert-warning rounded-none">
          <span>Could not load the task&apos;s output from TES.</span>
        </div>
      ) : attempts.every((attempt) => (attempt.logs ?? []).length === 0) ? (
        <p className="text-sm">No executor has run yet.</p>
      ) : (
        attempts.map((attempt, attemptIndex) =>
          (attempt.logs ?? []).map((executor, executorIndex) => (
            <div key={`${attemptIndex}/${executorIndex}`} className="border-l-4 border-base-300 pl-3">
              <div className="text-sm mb-1">
                {attempts.length > 1 ? `Attempt ${attemptIndex + 1}, ` : ""}
                executor {executorIndex + 1}
                {executor.exit_code !== undefined && executor.exit_code !== null
                  ? `: exit code ${executor.exit_code}`
                  : ""}
              </div>
              <ExecutorOutput name="stdout" text={executor.stdout} />
              <ExecutorOutput name="stderr" text={executor.stderr} />
            </div>
          )),
        )
      )}
    </div>
  );
}

// the run's TES tasks (WES GET /runs/{run_id}/tasks), each with its state from TES. A task opens to show its
// executors' output (TES FULL view)
export default function RunTasksSection({ platformId, runPath, runState, now }: RunTasksSectionProps) {
  const [loaded, setLoaded] = useState<LoadedTasks | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  // each task's state, from TES
  const [states, setStates] = useState<Record<string, string>>({});
  // the opened tasks, with their FULL view (undefined while it loads, null when TES didn't answer)
  const [opened, setOpened] = useState<Record<string, TesTask | null | undefined>>({});

  // what the refresh reads without restarting its timer on every change
  const latest = useRef({ loaded, states, opened });
  useEffect(() => {
    latest.current = { loaded, states, opened };
  });

  // the newest page of tasks, then TES's copy of each task shown that isn't known to be finished: the FULL view of
  // the opened ones, the state of the rest. Only fetches: apply puts what it found into the page
  const collect = useCallback(async (): Promise<Refreshed> => {
    let page: LoadedTasks;
    try {
      page = await fetchTasksPage(runPath, "");
    } catch (err) {
      return { error: err instanceof Error ? err.message : "the tasks couldn't be loaded" };
    }
    const current = latest.current;
    const shown = current.loaded ? withRefreshedPage(current.loaded, page) : page;
    const pending = shown.tasks.filter((task) => !isFinished(current.states[task.id] ?? ""));
    const fetched = await Promise.all(
      pending.map(async (task) => {
        const isOpen = task.id in current.opened;
        return [task.id, isOpen, await fetchTesTask(platformId, task.id, isOpen ? "FULL" : "MINIMAL")] as const;
      }),
    );
    return { page, fetched };
  }, [platformId, runPath]);

  const apply = useCallback((result: Refreshed) => {
    if ("error" in result) {
      setError(result.error);
      return;
    }
    // folded into what's loaded by then, so a page "Load more" added meanwhile stays
    setLoaded((current) => (current ? withRefreshedPage(current, result.page) : result.page));
    setError(null);
    setStates((previous) => {
      const next = { ...previous };
      for (const [id, , tesTask] of result.fetched) {
        if (tesTask) {
          next[id] = tesTask.state;
        }
      }
      return next;
    });
    setOpened((previous) => {
      const next = { ...previous };
      for (const [id, isOpen, tesTask] of result.fetched) {
        if (isOpen && id in next && tesTask) {
          next[id] = tesTask;
        }
      }
      return next;
    });
  }, []);

  // the first page, and again whenever the run's state changes
  useEffect(() => {
    let current = true;
    collect().then((result) => {
      if (current) {
        apply(result);
      }
    });
    return () => {
      current = false;
    };
  }, [collect, apply, runState]);

  const hasUnfinishedTasks = loaded !== null && loaded.tasks.some((task) => !isFinished(states[task.id] ?? ""));
  const keepRefreshing = !isFinished(runState) || hasUnfinishedTasks;

  useEffect(() => {
    if (!keepRefreshing) {
      return;
    }
    const timer = setInterval(async () => apply(await collect()), REFRESH_MILLISECONDS);
    return () => clearInterval(timer);
  }, [keepRefreshing, collect, apply]);

  const loadMore = async () => {
    if (!loaded?.nextPageToken) {
      return;
    }
    setIsLoadingMore(true);
    try {
      const page = await fetchTasksPage(runPath, loaded.nextPageToken);
      setLoaded((current) =>
        current === null ? page : { tasks: [...current.tasks, ...page.tasks], nextPageToken: page.nextPageToken },
      );
      const fetched = await Promise.all(
        page.tasks.map(async (task) => [task.id, await fetchTesTask(platformId, task.id, "MINIMAL")] as const),
      );
      setStates((previous) => {
        const next = { ...previous };
        for (const [id, tesTask] of fetched) {
          if (tesTask) {
            next[id] = tesTask.state;
          }
        }
        return next;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "the tasks couldn't be loaded");
    }
    setIsLoadingMore(false);
  };

  // open a task (fetching its FULL view), or close it
  const toggle = async (taskId: string) => {
    if (taskId in opened) {
      setOpened((previous) => {
        const next = { ...previous };
        delete next[taskId];
        return next;
      });
      return;
    }
    setOpened((previous) => ({ ...previous, [taskId]: undefined }));
    const tesTask = await fetchTesTask(platformId, taskId, "FULL");
    setOpened((previous) => (taskId in previous ? { ...previous, [taskId]: tesTask } : previous));
    if (tesTask) {
      setStates((previous) => ({ ...previous, [taskId]: tesTask.state }));
    }
  };

  const timing = (task: TaskLog) => {
    if (!task.start_time) {
      return null;
    }
    return (
      <>
        <div>Started {formatTime(task.start_time)}</div>
        <div className="opacity-70">
          {task.end_time
            ? `Ended ${formatTime(task.end_time)} (${formatDuration(task.start_time, new Date(task.end_time))})`
            : `Running for ${formatDuration(task.start_time, now)}`}
        </div>
      </>
    );
  };

  return (
    <section className="mb-8">
      <h2 className="mb-2 text-2xl">Tasks</h2>
      <p className="mb-4">
        The TES tasks the workflow engine ran for this run, newest first. Open a task to see its executors&apos; output.
      </p>

      {error ? (
        <div role="alert" className="alert alert-warning mb-4 rounded-none">
          <span>Could not load the run&apos;s tasks: {error}.</span>
        </div>
      ) : null}

      {loaded === null ? (
        error ? null : (
          <p>Loading the tasks…</p>
        )
      ) : loaded.tasks.length === 0 ? (
        <p className="p-4 bg-base-200 rounded-none">
          {isFinished(runState) ? "This run didn't start any tasks." : "No tasks have started yet."}
        </p>
      ) : (
        <>
          <div className="w-full overflow-x-auto border border-base-300 rounded-none">
            <table className="ga4gh-data-table table table-zebra table-sm w-full rounded-none">
              <thead>
                <tr>
                  <th className="bg-base-200 text-base-content">Task</th>
                  <th className="bg-base-200 text-base-content">State</th>
                  <th className="bg-base-200 text-base-content">Time</th>
                  <th className="bg-base-200 text-base-content">Exit code</th>
                  <th className="bg-base-200 text-base-content">
                    <span className="sr-only">Output</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {loaded.tasks.map((task) => {
                  const isOpen = task.id in opened;
                  const state = states[task.id];
                  return (
                    <Fragment key={task.id}>
                      <tr>
                        <td data-label="Task" className="break-all">
                          {task.name ?? task.id}
                        </td>
                        <td data-label="State">
                          {state ? <span className={statePillClass(state)}>{stateLabel(state)}</span> : "…"}
                        </td>
                        <td data-label="Time" className="whitespace-nowrap">
                          {timing(task)}
                        </td>
                        <td data-label="Exit code">{task.exit_code ?? ""}</td>
                        <td data-label="Output">
                          <button
                            type="button"
                            className="btn btn-xs rounded-none"
                            aria-expanded={isOpen}
                            onClick={() => toggle(task.id)}
                          >
                            {isOpen ? "Hide output" : "Show output"}
                          </button>
                        </td>
                      </tr>
                      {isOpen ? (
                        <tr>
                          <td colSpan={5}>
                            <TaskDetails task={task} tesTask={opened[task.id]} />
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
          {loaded.nextPageToken ? (
            <button
              type="button"
              className="ga4gh-btn-dark aspect-auto mt-4"
              onClick={loadMore}
              disabled={isLoadingMore}
            >
              <span className="btn-text">{isLoadingMore ? "Loading…" : "Load more tasks"}</span>
            </button>
          ) : null}
        </>
      )}
    </section>
  );
}
