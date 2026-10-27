import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const loginChallenge = searchParams.get('login_challenge');
  
  if (!loginChallenge) {
    return NextResponse.json({ error: 'Missing challenge' }, { status: 400 });
  }

  const cookieStore = await cookies();
  const kratosCookie = cookieStore.get('ory_kratos_session')?.value;

  const kratosRes = await fetch(`${process.env.KRATOS_PUBLIC_API_BASE_URL}/sessions/whoami`, {
    headers: { 'Cookie': `ory_kratos_session=${kratosCookie}` },
    cache: 'no-store',
  });

  if (!kratosRes.ok) {
    return NextResponse.redirect(`${process.env.HYDRA_ADMIN_API_BASE_URL}/login?login_challenge=${loginChallenge}`);
  }

  const sessionData = await kratosRes.json();
  const userId = sessionData.identity.id;

  try {
    const hydraAcceptRes = await fetch(
      `${process.env.HYDRA_ADMIN_API_BASE_URL}/admin/oauth2/auth/requests/login/accept?login_challenge=${loginChallenge}`, 
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          subject: userId,
          remember: true,
          remember_for: 3600,
        }),
      }
    );

    if (!hydraAcceptRes.ok) {
      const errText = await hydraAcceptRes.text();
      return NextResponse.json({ error: 'Failed to accept login challenge', details: errText }, { status: 500 });
    }

    const hydraData = await hydraAcceptRes.json();

    return NextResponse.redirect(hydraData.redirect_to);

  } catch (error) {
    console.error('OAuth Login bridge handler runtime crash:', error);
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}
