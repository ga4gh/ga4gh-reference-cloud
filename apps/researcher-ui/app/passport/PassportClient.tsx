'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useEffect } from 'react';
import AppLayout from '@/components/layout/AppLayout';
import PassportTokenDisplay from '@/components/passport/PassportTokenDisplay';
import { useEnv } from '@/context/EnvContext';

export default function PassportClient() {
  const env = useEnv();
  const router = useRouter();

  const searchParams = useSearchParams();
  const code = searchParams?.get('code') || undefined;
  const state = searchParams?.get('state') || undefined;

  const [isProcessingExchange, setIsProcessingExchange] = useState(false);
  const [flowComplete, setFlowComplete] = useState(false);

  useEffect(() => {
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
          redirect_uri: `${env.UI_BASE_URL}/passport`
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
          setIsProcessingExchange(false); // Reset so it could try again if needed
        }
      })
      .catch((err) => {
        console.error("Network crash during swap:", err);
        setIsProcessingExchange(false);
      });

      return; // CRITICAL: Stop execution here. Do NOT run initial redirect logic.
    }

    // PIPELINE B: NO CODE & NO TOKEN YET -> INITIATE THE LOGIN REDIRECT ONLY ONCE
    if (!flowComplete && !code) {
      console.log("No token or code found. Transitioning window control to Ory Hydra...");

      const hydraAuthUrl = new URL(`${env.HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL}/oauth2/auth`);
      hydraAuthUrl.searchParams.append('client_id', `${env.HYDRA_RESEARCHER_CLIENT_ID}`);
      hydraAuthUrl.searchParams.append('response_type', 'code');
      hydraAuthUrl.searchParams.append('scope', 'openid offline_access profile');
      hydraAuthUrl.searchParams.append('redirect_uri', `${env.UI_BASE_URL}/passport`);
      hydraAuthUrl.searchParams.append('state', state || crypto.randomUUID());

      // Transfer window execution entirely out of Next.js state engine
      window.location.href = hydraAuthUrl.toString();
    }
  }, [code, state, flowComplete, isProcessingExchange, env, router]);

  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">View Passport Token</h1>
      <h2 className="mb-4 text-2xl">Use your passport token to access data via GA4GH APIs</h2>
      {flowComplete ? (
        <PassportTokenDisplay />
      ) : (
        <h2 className="mb-4 text-xl">preparing passport token...</h2>
      )}
    </AppLayout>
  );
}
