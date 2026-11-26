import { NextRequest } from "next/server";
import { encodeSegments, forwardToCoreApi, invalidPath } from "@/utils/api/ga4ghProxy";

interface RouteContext {
  params: Promise<{ platformId: string; runId: string; stream: string }>;
}

const STREAMS = new Set(["stdout", "stderr"]);

// proxies the refcloud's WES GET /runs/{run_id}/logs/{stdout|stderr} (non-standard): the engine's console output, as
// plain text, streamed. With ?download=1 it saves as <run id>-<stream>.log
export async function GET(request: NextRequest, context: RouteContext) {
  const { platformId, runId, stream } = await context.params;
  const path = STREAMS.has(stream) ? encodeSegments(platformId, "v1", "runs", runId, "logs", stream) : null;
  if (!path) return invalidPath();
  const download = request.nextUrl.searchParams.get("download") === "1";
  return forwardToCoreApi(`/ga4gh/wes/${path}`, {
    stream: true,
    download: download ? `${runId}-${stream}.log` : undefined,
    label: `WES run ${runId} ${stream}`,
  });
}
