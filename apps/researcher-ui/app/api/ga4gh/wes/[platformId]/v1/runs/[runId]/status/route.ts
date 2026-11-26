import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string }>;
}

// proxies WES GET /runs/{run_id}/status: the run's state, which the run's page polls while the run is active
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, runId } = await context.params;
  const path = encodeSegments(platformId, "v1", "runs", runId, "status");
  if (!path) return invalidPath();
  return forwardToCoreApi(`/ga4gh/wes/${path}`, { label: `WES run ${runId} status` });
}
