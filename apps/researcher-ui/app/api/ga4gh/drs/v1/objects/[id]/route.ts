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
  const cookieStore = await cookies();
  const accessToken = cookieStore.get('access_token')?.value;

  if (!apiBaseUrl || !accessToken) {
    return NextResponse.json({ message: 'Internal Server Error' }, { status: 500 });
  }

  try {
    const targetUrl = `${apiBaseUrl}/ga4gh/drs/v1/objects/${id}`;
    
    const result = await fetch(targetUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });

    const data = await result.json().catch(() => null);
    
    return NextResponse.json(data, { status: result.status });
  } catch (error) {
    console.error(`Backend connectivity failure on DRS object ${id}:`, error);
    return NextResponse.json({ message: 'DRS Gateway Failure' }, { status: 502 });
  }
}
