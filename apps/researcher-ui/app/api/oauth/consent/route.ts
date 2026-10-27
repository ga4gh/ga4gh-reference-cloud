import { NextRequest, NextResponse } from 'next/server';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const consent_challenge = searchParams.get('consent_challenge');

  if (!consent_challenge) {
    return NextResponse.json(
      { error: 'Missing consent_challenge' }, 
      { status: 400, headers: getNoCacheHeaders() }
    );
  }

  try {
    const hydraGetRes = await fetch(
      `${process.env.HYDRA_ADMIN_API_BASE_URL}/oauth2/auth/requests/consent?consent_challenge=${consent_challenge}`,
      { cache: 'no-store' }
    );

    if (!hydraGetRes.ok) {
      const errText = await hydraGetRes.text();
      return NextResponse.json(
        { error: 'Failed to fetch consent details', details: errText }, 
        { status: 500, headers: getNoCacheHeaders() }
      );
    }

    const consentRequestDetails = await hydraGetRes.json();

    const hydraAcceptRes = await fetch(
      `${process.env.HYDRA_ADMIN_API_BASE_URL}/oauth2/auth/requests/consent/accept?consent_challenge=${consent_challenge}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          grant_scope: consentRequestDetails.requested_scope,
          grant_access_token_audience: consentRequestDetails.requested_access_token_audience,
          remember: true,
          remember_for: 3600,
        }),
      }
    );

    if (!hydraAcceptRes.ok) {
      const errorText = await hydraAcceptRes.text();
      return NextResponse.json(
        { error: 'Failed to accept Hydra consent', details: errorText }, 
        { status: 500, headers: getNoCacheHeaders() }
      );
    }

    const hydraData = await hydraAcceptRes.json();
    const redirectResponse = NextResponse.redirect(hydraData.redirect_to);

    redirectResponse.headers.set('Cache-Control', 'no-store, no-cache, must-revalidate');
    
    return redirectResponse;

  } catch (error) {
    console.error('Consent handler runtime crash:', error);
    return NextResponse.json(
      { error: 'Internal Server Error' }, 
      { status: 500, headers: getNoCacheHeaders() }
    );
  }
}

function getNoCacheHeaders() {
  const headers = new Headers();
  headers.set('Cache-Control', 'no-store, no-cache, must-revalidate');
  return headers;
}
