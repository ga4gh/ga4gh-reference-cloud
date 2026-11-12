import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";

interface RouteContext {
  params: Promise<{
    name: string;
  }>;
}

// proxies Data Connect GET /table/{table_name}/data. the client never follows next_page_url
// directly: it extracts page_token from it and passes it back here
export async function GET(request: NextRequest, context: RouteContext) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  const { name } = await context.params;
  const pageToken = request.nextUrl.searchParams.get("page_token");

  const cookieStore = await cookies();
  const accessToken = cookieStore.get("access_token")?.value;

  if (!apiBaseUrl) {
    return NextResponse.json({ message: "Internal Server Error" }, { status: 500 });
  }

  try {
    const targetUrl = new URL(`${apiBaseUrl}/ga4gh/data-connect/v1/table/${encodeURIComponent(name)}/data`);
    if (pageToken) {
      targetUrl.searchParams.set("page_token", pageToken);
    }

    // without a passport token the request is forwarded anonymously (platform-wide and open tables only).
    // an invalid or expired token is rejected by core-api with 401, which is passed through as is
    const headers: HeadersInit = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

    const result = await fetch(targetUrl, {
      method: "GET",
      headers,
    });

    // Data Connect errors carry an ErrorResponse body, passed through with the original status
    const data = await result.json().catch(() => null);

    return NextResponse.json(data, { status: result.status });
  } catch (error) {
    console.error(`Backend connectivity failure on Data Connect table ${name} data:`, error);
    return NextResponse.json({ message: "Data Connect Gateway Failure" }, { status: 502 });
  }
}
