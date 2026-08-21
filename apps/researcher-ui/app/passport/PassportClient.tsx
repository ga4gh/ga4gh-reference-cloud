// app/passport/PassportClient.tsx
'use client';

import { useState, useEffect } from 'react';
import PassportTokenDisplay from '@/components/pages/passport/PassportTokenDisplay';
import { useEnv } from '@/components/context/EnvContext';

export default function PassportClient() {
  const env = useEnv();
  const [flowComplete, setFlowComplete] = useState(false);
  const [isAuthTriggered, setIsAuthTriggered] = useState(false);

  useEffect(() => {
    // 💡 Listen for the postMessage signal from the invisible iframe
    const handleAuthMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === 'PASSPORT_AUTH_SUCCESS') {
        console.log("🔒 Invisible frame auth success! Rendering token...");
        setFlowComplete(true);
        
        // Clean up the iframe from the DOM once finished
        const frame = document.getElementById('hydra-auth-frame');
        if (frame) frame.remove();
      }
    };

    window.addEventListener('message', handleAuthMessage);
    
    // Automatically trigger the hidden flow on mount if not authenticated yet
    if (!flowComplete && !isAuthTriggered) {
      setIsAuthTriggered(true);
      console.log("Launching hidden background authentication flow...");

      const hydraAuthUrl = new URL(`${env.HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL}/oauth2/auth`);
      hydraAuthUrl.searchParams.append('client_id', `${env.HYDRA_RESEARCHER_CLIENT_ID}`);
      hydraAuthUrl.searchParams.append('response_type', 'code');
      hydraAuthUrl.searchParams.append('scope', 'openid offline_access profile');
      hydraAuthUrl.searchParams.append('redirect_uri', `${env.UI_BASE_URL}/passport/callback`);
      hydraAuthUrl.searchParams.append('state', crypto.randomUUID());

      // Create an invisible iframe
      const iframe = document.createElement('iframe');
      iframe.id = 'hydra-auth-frame';
      iframe.src = hydraAuthUrl.toString();
      // Hide it completely from view and assistive technologies
      iframe.style.display = 'none';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.position = 'absolute';
      
      document.body.appendChild(iframe);
    }

    return () => window.removeEventListener('message', handleAuthMessage);
  }, [flowComplete, isAuthTriggered, env]);

  return (
    <div className="mt-6">
      {flowComplete ? (
        <PassportTokenDisplay />
      ) : (
        <div className="flex flex-col gap-4">
          <h2 className="mb-4 text-xl font-medium flex items-center gap-2">
            <span className="loading loading-ring loading-md text-primary"></span>
            Preparing secure connection keys in background...
          </h2>
          <div className="h-[200px] bg-base-200 w-full max-w-4xl rounded-box animate-pulse" />
        </div>
      )}
    </div>
  );
}
