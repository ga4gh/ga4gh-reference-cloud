import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json().catch(() => ({}));
    const { code, redirect_uri } = body;

    if (!code) {
      return NextResponse.json({ error: 'Missing authorization code parameter' }, { status: 400 });
    }

    const hydraTokenRes = await fetch(`${process.env.HYDRA_PUBLIC_API_SERVER_SIDE_BASE_URL}/oauth2/token`, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/x-www-form-urlencoded' 
      },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: redirect_uri,
        client_id: `${process.env.PUB_HYDRA_RESEARCHER_CLIENT_ID}`,
        client_secret: `${process.env.HYDRA_RESEARCHER_CLIENT_SECRET}`
      }),
      cache: 'no-store'
    });

    const tokenData = await hydraTokenRes.json();

    if (!hydraTokenRes.ok) {
      console.error('❌ Hydra Token Exchange Rejected Payload:', tokenData);
      return NextResponse.json({ error: 'Hydra rejected code swap', details: tokenData }, { status: 400 });
    }

    const jwtAccessToken = tokenData.access_token;

    const response = NextResponse.json({ success: true }, { status: 200 });

    const cookieHeaders: string[] = [
      `access_token=${jwtAccessToken}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${tokenData.expires_in || 3600};`
    ];

    if (tokenData.refresh_token) {
      cookieHeaders.push(`refresh_token=${tokenData.refresh_token}; Path=/; HttpOnly; SameSite=Lax;`);
    }

    cookieHeaders.forEach(cookieStr => {
      response.headers.append('Set-Cookie', cookieStr);
    });

    return response;

  } catch (error) {
    console.error('💥 Critical breakdown inside Token Exchange Handler:', error);
    return NextResponse.json({ error: 'Internal Server Error during token compilation' }, { status: 500 });
  }
}
