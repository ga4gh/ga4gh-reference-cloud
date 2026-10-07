import type { NextApiRequest, NextApiResponse } from 'next';

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  // 1. Only allow secure POST traffic
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method Not Allowed' });
  }

  const { code, redirect_uri } = req.body;

  if (!code) {
    return res.status(400).json({ error: 'Missing authorization code parameter' });
  }

  try {
    // 2. Perform the backchannel code-to-token swap with Ory Hydra
    // This is where you pass your client_secret safely out of browser sight.
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
    });

    const tokenData = await hydraTokenRes.json();

    if (!hydraTokenRes.ok) {
      console.error('❌ Hydra Token Exchange Rejected Payload:', tokenData);
      return res.status(400).json({ error: 'Hydra rejected code swap', details: tokenData });
    }

    // 3. Extract the minted RS256/ES256 Access Token (This is your JWT)
    const jwtAccessToken = tokenData.access_token;

    // 4. Bake the token into a Secure, HttpOnly cookie.
    // This shields the token from JavaScript access (thwarting XSS vectors)
    // while ensuring the browser appends it to subsequent internal requests automatically.
    res.setHeader('Set-Cookie', [
      `access_token=${jwtAccessToken}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${tokenData.expires_in || 3600};`,
      // Optional: If a refresh token was returned, you can save it securely here too
      ...(tokenData.refresh_token ? [`refresh_token=${tokenData.refresh_token}; Path=/; HttpOnly; SameSite=Lax;`] : [])
    ]);

    // 5. Respond with a success state back to your React client component
    return res.status(200).json({ success: true });

  } catch (error) {
    console.error('💥 Critical breakdown inside Token Exchange Handler:', error);
    return res.status(500).json({ error: 'Internal Server Error during token compilation' });
  }
}