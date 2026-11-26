import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string; path: string[] }>;
}

// proxies the refcloud's WES GET /runs/{run_id}/outputs/{path} (non-standard): one of the run's output files,
// streamed, saved under core-api's file name. A plain link can't send the passport, so the run's page links here
// instead of the outputs' own URLs
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, runId, path } = await context.params;
  const target = encodeSegments(platformId, "v1", "runs", runId, "outputs", ...path);
  if (!target || path.length === 0) return invalidPath();
  return forwardToCoreApi(`/ga4gh/wes/${target}`, { stream: true, label: `WES run ${runId} output` });
}
