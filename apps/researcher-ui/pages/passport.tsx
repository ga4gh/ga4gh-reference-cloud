import type { NextPage } from 'next'
import { useRouter } from 'next/router';
import { useState, useEffect } from 'react';
import AppLayout from 'components/layout/AppLayout';
import PassportTokenDisplay from 'components/passport/PassportTokenDisplay';

const Passport: NextPage = () => {
  const router = useRouter();
  const { code, state } = router.query;
  const [isProcessingExchange, setIsProcessingExchange] = useState(false);
  const [flowComplete, setFlowComplete] = useState(false);

  useEffect(() => {
    // Wait for Next.js router to fully parse query parameters on mount
    if (!router.isReady) return;

    if (flowComplete) {
      console.log("Token already present. Escaping loop to Passport Page.");
      router.push('/passport');
      return;
    }

    // PIPELINE A: WE HAVE AN OAUTH CODE FROM HYDRA -> RUN EXCHANGE ONLY
    if (!flowComplete && code && typeof code === 'string') {
      if (isProcessingExchange) return; // Prevent double-triggering exchange
      setIsProcessingExchange(true);

      console.log("Found Hydra Code. Swapping for JWT via Server API...");

      fetch('/api/oauth/token-exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code,
          redirect_uri: `${process.env.NEXT_PUBLIC_BASE_URL}/passport`
        })
      })
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          // Token is saved securely as an HttpOnly cookie via the server handler
          setFlowComplete(true);
          router.push('/passport');
        } else {
          console.error("Token swap failed:", data.details);
        }
      })
      .catch((err) => console.error("Network crash during swap:", err));

      return; // CRITICAL: Stop execution here. Do NOT run initial redirect logic.
    }

    // PIPELINE B: NO CODE & NO TOKEN YET -> INITIATE THE LOGIN REDIRECT ONLY ONCE
    if (!flowComplete && !code) {
      console.log("No token or code found. Transitioning window control to Ory Hydra...");

      const hydraAuthUrl = new URL(`${process.env.NEXT_PUBLIC_HYDRA_PUBLIC_API_BASE_URL}/oauth2/auth`);
      hydraAuthUrl.searchParams.append('client_id', `${process.env.NEXT_PUBLIC_HYDRA_RESEARCHER_CLIENT_ID}`);
      hydraAuthUrl.searchParams.append('response_type', 'code');
      hydraAuthUrl.searchParams.append('scope', 'openid offline_access profile');
      hydraAuthUrl.searchParams.append('redirect_uri', `${process.env.NEXT_PUBLIC_BASE_URL}/passport`);
      hydraAuthUrl.searchParams.append('state', (state as string) || crypto.randomUUID());

      // Transfer window execution entirely out of Next.js state engine
      window.location.href = hydraAuthUrl.toString();
    }
  }, [router.isReady, code, state]);

  return (
    <>
      <AppLayout>
        <h1 className="mb-5 text-5xl font-bold">View Passport Token</h1>
        <h2 className="mb-4 text-2xl">Use your passport token to access data via GA4GH APIs</h2>
        {flowComplete ? <PassportTokenDisplay/> : <h2 className="mb-4 text-2xl">Preparing passport token</h2>}
      </AppLayout>
    </>
  )
}

export default Passport
