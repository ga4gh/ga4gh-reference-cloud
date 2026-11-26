import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string }>;
}

// proxies WES GET /runs/{run_id}: the run's log (its request, state, run_log, task_logs_url and outputs)
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, runId } = await context.params;
  const path = encodeSegments(platformId, "v1", "runs", runId);
  if (!path) return invalidPath();
  return forwardToCoreApi(`/ga4gh/wes/${path}`, { label: `WES run ${runId}` });
}
