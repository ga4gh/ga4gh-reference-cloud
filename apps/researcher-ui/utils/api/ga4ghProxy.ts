import { NextResponse } from "next/server";
import { cookies } from "next/headers";

// forwards a request to one of core-api's GA4GH APIs (WES and TES for My Runs, spec 0014) with the researcher's
// passport, from the access_token cookie, as the other GA4GH proxy routes do (see CLAUDE.md, "GA4GH API proxy
// routes"). Without the cookie the request goes anonymously, and core-api refuses it. Statuses and error bodies
// pass through unchanged; 502 only when core-api can't be reached.
//
// path: core-api's path, with every dynamic segment already encoded (encodeSegments). query: only the parameters
// the route accepts. stream: pass the body through as is (logs, output files), with its Content-Type and
// Content-Disposition; otherwise it's parsed as JSON. download: a file name, to save a streamed body as a file
export async function forwardToCoreApi(
  path: string,
  options: {
    method?: "GET" | "POST";
    query?: Record<string, string | null>;
    stream?: boolean;
    download?: string;
    label: string;
  },
): Promise<NextResponse> {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  if (!apiBaseUrl) {
    return NextResponse.json({ message: "Internal Server Error" }, { status: 500 });
  }

  const cookieStore = await cookies();
  const accessToken = cookieStore.get("access_token")?.value;

  const targetUrl = new URL(`${apiBaseUrl}${path}`);
  for (const [name, value] of Object.entries(options.query ?? {})) {
    if (value) {
      targetUrl.searchParams.set(name, value);
    }
  }
  const headers: HeadersInit = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

  let result: Response;
  try {
    result = await fetch(targetUrl, { method: options.method ?? "GET", headers, cache: "no-store" });
  } catch (error) {
    console.error(`Backend connectivity failure on ${options.label}:`, error);
    return NextResponse.json({ message: "Gateway Failure" }, { status: 502 });
  }

  if (!options.stream || !result.ok) {
    const data = await result.json().catch(() => null);
    return NextResponse.json(data, { status: result.status });
  }

  const responseHeaders: Record<string, string> = {
    "Content-Type": result.headers.get("Content-Type") ?? "application/octet-stream",
  };
  const disposition = options.download
    ? `attachment; filename="${safeFilename(options.download)}"`
    : result.headers.get("Content-Disposition");
  if (disposition) {
    responseHeaders["Content-Disposition"] = disposition;
  }
  return new NextResponse(result.body, { status: result.status, headers: responseHeaders });
}

// a URL path from route segments, each one encoded, so a segment can't add path levels; null if one is empty, "." or
// "..", which a URL would resolve away (climbing out of the path the route means to reach)
export function encodeSegments(...segments: string[]): string | null {
  if (segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
    return null;
  }
  return segments.map((segment) => encodeURIComponent(segment)).join("/");
}

// the response to a request whose path segments encodeSegments refused
export function invalidPath(): NextResponse {
  return NextResponse.json({ message: "Invalid path" }, { status: 400 });
}

// a file name for Content-Disposition: no quotes, backslashes or line breaks
function safeFilename(name: string): string {
  return name.replace(/["\\\r\n]/g, "_");
}
