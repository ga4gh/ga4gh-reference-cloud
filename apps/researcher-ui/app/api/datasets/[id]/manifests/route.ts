import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';

interface RouteContext {
  params: Promise<{
    id: string;
  }>;
}

export async function GET(request: NextRequest, context: RouteContext) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  const { id } = await context.params;
  const searchParams = request.nextUrl.searchParams;
  const page = searchParams.get('page') || '0';
  const size = searchParams.get('size') || '10';

  const cookieStore = await cookies();
  const accessToken = cookieStore.get('access_token')?.value;

  if (!apiBaseUrl || !accessToken) {
    return NextResponse.json({ message: 'Internal Server Error' }, { status: 500 });
  }

  try {
    const targetUrl = `${apiBaseUrl}/datasets/${id}/manifests?page=${page}&size=${size}`;
    
    const result = await fetch(targetUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });

    const data = await result.json().catch(() => null);
    
    return NextResponse.json(data, { status: result.status });
  } catch (error) {
    console.error(`Backend connectivity failure on dataset ${id} manifests:`, error);
    return NextResponse.json({ message: 'Gateway Fetch Failure' }, { status: 502 });
  }
}
