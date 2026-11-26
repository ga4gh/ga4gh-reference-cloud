import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string }>;
}

// proxies WES GET /runs: the researcher's runs on one platform, newest first, paged (page_size, page_token), each
// with its workflow_url (spec 0014)
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId } = await context.params;
  const platform = encodeSegments(platformId);
  if (!platform) return invalidPath();
  const search = request.nextUrl.searchParams;
  return forwardToCoreApi(`/ga4gh/wes/${platform}/v1/runs`, {
    query: { page_size: search.get("page_size"), page_token: search.get("page_token") },
    label: `WES runs on ${platformId}`,
  });
}
