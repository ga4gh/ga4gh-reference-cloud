import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string }>;
}

// proxies WES GET /runs/{run_id}/tasks: the run's TES tasks, paged (page_size, page_token), each with its tes_uri
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, runId } = await context.params;
  const path = encodeSegments(platformId, "v1", "runs", runId, "tasks");
  if (!path) return invalidPath();
  const search = request.nextUrl.searchParams;
  return forwardToCoreApi(`/ga4gh/wes/${path}`, {
    query: { page_size: search.get("page_size"), page_token: search.get("page_token") },
    label: `WES run ${runId} tasks`,
  });
}
