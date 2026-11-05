import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";

interface RouteContext {
  params: Promise<{
    id: string;
  }>;
}

// proxies DRS GET /objects/{id}/manifest-content, the access URL of manifest objects. a plain browser
// download link can't send the passport, so DrsObjectDownloadButton links here for manifests instead
export async function GET(request: NextRequest, context: RouteContext) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  const { id } = await context.params;

  const cookieStore = await cookies();
  const accessToken = cookieStore.get("access_token")?.value;

  if (!apiBaseUrl) {
    return NextResponse.json({ message: "Internal Server Error" }, { status: 500 });
  }

  try {
    const targetUrl = `${apiBaseUrl}/ga4gh/drs/v1/objects/${encodeURIComponent(id)}/manifest-content`;

    // without a passport token the request is forwarded anonymously (objects in open datasets only)
    const headers: HeadersInit = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

    const result = await fetch(targetUrl, {
      method: "GET",
      headers,
    });

    const responseHeaders: HeadersInit = { "Content-Type": result.headers.get("Content-Type") ?? "application/json" };
    if (result.ok) {
      // save as a file named after the manifest's DRS ID
      responseHeaders["Content-Disposition"] = `attachment; filename="${id.replace(/["\\\r\n]/g, "_")}"`;
    }

    return new NextResponse(result.body, { status: result.status, headers: responseHeaders });
  } catch (error) {
    console.error(`Backend connectivity failure on DRS object ${id} manifest content:`, error);
    return NextResponse.json({ message: "DRS Gateway Failure" }, { status: 502 });
  }
}
