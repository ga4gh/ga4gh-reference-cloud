import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string }>;
}

// proxies WES POST /runs/{run_id}/cancel. The passport cookie is SameSite=Lax, so another site's form can't post here
// with it. The UI submits no runs: there's no proxy for POST /runs (spec 0014, out of scope)
export async function POST(request: NextRequest, context: RouteContext) {
  const { platformId, runId } = await context.params;
  const path = encodeSegments(platformId, "v1", "runs", runId, "cancel");
  if (!path) return invalidPath();
  return forwardToCoreApi(`/ga4gh/wes/${path}`, { method: "POST", label: `WES run ${runId} cancel` });
}
