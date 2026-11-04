import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';

interface RouteContext {
  params: Promise<{
    id: string;
  }>;
}

// proxies refget GET /sequence/{id}?start=&end= (0-based, end-exclusive). the sequence (plain text)
// or error (JSON) is streamed back unchanged, with its status and content type
export async function GET(request: NextRequest, context: RouteContext) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  const { id } = await context.params;
  const searchParams = request.nextUrl.searchParams;

  const cookieStore = await cookies();
  const accessToken = cookieStore.get('access_token')?.value;

  if (!apiBaseUrl) {
    return NextResponse.json({ message: 'Internal Server Error' }, { status: 500 });
  }

  try {
    const targetUrl = new URL(`${apiBaseUrl}/ga4gh/refget/v2/sequence/${encodeURIComponent(id)}`);
    for (const param of ['start', 'end']) {
      const value = searchParams.get(param);
      if (value !== null) targetUrl.searchParams.set(param, value);
    }

    // without a passport token the request is forwarded anonymously (sequences in open datasets only)
    const headers: HeadersInit = accessToken ? { 'Authorization': `Bearer ${accessToken}` } : {};

    const result = await fetch(targetUrl, {
      method: 'GET',
      headers
    });

    return new NextResponse(result.body, {
      status: result.status,
      headers: { 'Content-Type': result.headers.get('Content-Type') ?? 'text/plain' }
    });
  } catch (error) {
    console.error(`Backend connectivity failure on refget sequence ${id}:`, error);
    return NextResponse.json({ message: 'Refget Gateway Failure' }, { status: 502 });
  }
}
