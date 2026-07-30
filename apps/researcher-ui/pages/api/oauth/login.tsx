import { NextResponse } from 'next/server';
import type { NextApiRequest, NextApiResponse } from 'next';

export default async function GET(request: NextApiRequest, response: NextApiResponse) {
  const { login_challenge } = request.query;
  const loginChallenge = login_challenge;
  
  if (!loginChallenge) return NextResponse.json({ error: 'Missing challenge' }, { status: 400 });

  const kratosCookie = request.cookies['ory_kratos_session']

  // 1. Verify session with Kratos
  const kratosRes = await fetch(`${process.env.KRATOS_PUBLIC_API_BASE_URL}/sessions/whoami`, {
    headers: { 'Cookie': `ory_kratos_session=${kratosCookie}` }
  });

  if (!kratosRes.ok) {
    // If NOT logged in, redirect them to your Kratos login page 
    // Pass the login_challenge parameter so Kratos knows to return here
    return NextResponse.redirect(`${process.env.HYDRA_ADMIN_API_BASE_URL}/login?login_challenge=${loginChallenge}`);
  }

  const sessionData = await kratosRes.json();
  const userId = sessionData.identity.id; // Kratos Identity ID

  // 2. Since they ARE logged in, accept the Hydra login request immediately
  const hydraAcceptRes = await fetch(`${process.env.HYDRA_ADMIN_API_BASE_URL}/admin/oauth2/auth/requests/login/accept?login_challenge=${loginChallenge}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      subject: userId, // This maps the Kratos Identity ID to the JWT 'sub' claim
      remember: true,
      remember_for: 3600,
    }),
  });

  const hydraData = await hydraAcceptRes.json();
  
  // Redirect the browser back to Hydra to continue the flow seamlessly
  response.redirect(hydraData.redirect_to);
  return;
}
