import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";

interface RouteContext {
  params: Promise<{
    id: string;
  }>;
}

export async function GET(request: NextRequest, context: RouteContext) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  const { id } = await context.params;
  const searchParams = request.nextUrl.searchParams;
  const page = searchParams.get("page") || "0";
  const size = searchParams.get("size") || "10";

  const cookieStore = await cookies();
  const accessToken = cookieStore.get("access_token")?.value;

  if (!apiBaseUrl) {
    return NextResponse.json({ message: "Internal Server Error" }, { status: 500 });
  }

  try {
    const targetUrl = `${apiBaseUrl}/datasets/${id}/manifests?page=${page}&size=${size}`;

    // without a passport token the request is forwarded anonymously (open datasets only)
    const headers: HeadersInit = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

    const result = await fetch(targetUrl, {
      method: "GET",
      headers,
    });

    const data = await result.json().catch(() => null);

    return NextResponse.json(data, { status: result.status });
  } catch (error) {
    console.error(`Backend connectivity failure on dataset ${id} manifests:`, error);
    return NextResponse.json({ message: "Gateway Fetch Failure" }, { status: 502 });
  }
}
