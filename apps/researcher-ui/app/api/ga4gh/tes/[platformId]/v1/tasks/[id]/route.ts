import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; id: string }>;
}

const VIEWS = new Set(["MINIMAL", "BASIC", "FULL"]);

// proxies TES GET /tasks/{id} (view: MINIMAL, BASIC or FULL): a run's task, which researchers can only read. FULL has
// the task's state and each executor's stdout, stderr and exit code (spec 0014)
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, id } = await context.params;
  const path = encodeSegments(platformId, "v1", "tasks", id);
  const view = request.nextUrl.searchParams.get("view");
  if (!path || (view !== null && !VIEWS.has(view))) return invalidPath();
  return forwardToCoreApi(`/ga4gh/tes/${path}`, { query: { view }, label: `TES task ${id}` });
}
