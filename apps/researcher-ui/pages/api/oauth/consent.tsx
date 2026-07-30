import { NextResponse } from 'next/server';
import type { NextApiRequest, NextApiResponse } from 'next';

export default async function GET(request: NextApiRequest, response: NextApiResponse) {
  if (request.method !== 'GET') {
    return response.status(405).json({ error: 'Method Not Allowed' });
  }

  // 1. Clear caching to avoid stale browser reruns
  response.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');

  const { consent_challenge } = request.query;

  if (!consent_challenge || typeof consent_challenge !== 'string') {
    return response.status(400).json({ error: 'Missing consent_challenge' });
  }

  try {
    // 2. Fetch the metadata of the consent request from Hydra to see what scopes were requested
    const hydraGetRes = await fetch(
      `${process.env.HYDRA_ADMIN_API_BASE_URL}/oauth2/auth/requests/consent?consent_challenge=${consent_challenge}`
    );

    if (!hydraGetRes.ok) {
      const errText = await hydraGetRes.text();
      return response.status(500).json({ error: 'Failed to fetch consent details', details: errText });
    }

    const consentRequestDetails = await hydraGetRes.json();

    // 3. Since this is your first-party Next.js UI app, auto-accept all requested scopes
    const hydraAcceptRes = await fetch(
      `${process.env.HYDRA_ADMIN_API_BASE_URL}/oauth2/auth/requests/consent/accept?consent_challenge=${consent_challenge}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          grant_scope: consentRequestDetails.requested_scope, // Grant exactly what the client requested
          grant_access_token_audience: consentRequestDetails.requested_access_token_audience,
          remember: true,
          remember_for: 3600,
          // Optional: Add session metadata if your Spring Boot app needs custom context info
          // session: {
          //   access_token: {
          //     // custom_claim_key: "custom_claim_value"
          //   }
          // }
        }),
      }
    );

    if (!hydraAcceptRes.ok) {
      const errorText = await hydraAcceptRes.text();
      return response.status(500).json({ error: 'Failed to accept Hydra consent', details: errorText });
    }

    const hydraData = await hydraAcceptRes.json();
    
    // 4. CRITICAL: You MUST natively redirect the browser window back to Hydra's returned redirect URL.
    // If you return a json object here, Hydra will timeout and abort the transaction.
    response.redirect(hydraData.redirect_to);
    return;

  } catch (error) {
    console.error('Consent handler runtime crash:', error);
    return response.status(500).json({ error: 'Internal Server Error' });
  }
}
