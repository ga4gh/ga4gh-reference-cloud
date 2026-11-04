'use client';

import React, { createContext, useContext, useState, useEffect } from 'react';
import { useEnv } from './EnvContext';
import { usePathname } from 'next/navigation';

interface PassportTokenContextType {
  isTokenLoaded: boolean;
  isLoadingToken: boolean;
}

const PassportTokenContext = createContext<PassportTokenContextType | undefined>(undefined);

export function PassportTokenProvider({ children }: { children: React.ReactNode }) {
  const env = useEnv();
  const pathname = usePathname();
  const [isTokenLoaded, setIsTokenLoaded] = useState(false);
  const [isLoadingToken, setIsLoadingToken] = useState(true);

  useEffect(() => {
    const handleAuthMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === 'PASSPORT_AUTH_SUCCESS') {
        console.log("🔒 Passport Token Hook: Secure background cookie assigned!");
        setIsTokenLoaded(true);
        setIsLoadingToken(false);

        const frame = document.getElementById('global-hydra-auth-frame');
        if (frame) frame.remove();
      }
    };

    window.addEventListener('message', handleAuthMessage);

    const isAuthRoute = pathname === '/passport/callback' || 
                        pathname === '/login' || 
                        pathname === '/registration';

    if (!isTokenLoaded && env?.UI_BASE_URL && !isAuthRoute) {
      console.log("🚀 Booting centralized background passport token pipeline...");

      const hydraAuthUrl = new URL(`${env.HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL}/oauth2/auth`);
      hydraAuthUrl.searchParams.append('client_id', `${env.HYDRA_RESEARCHER_CLIENT_ID}`);
      hydraAuthUrl.searchParams.append('response_type', 'code');
      hydraAuthUrl.searchParams.append('scope', 'openid offline_access profile');
      hydraAuthUrl.searchParams.append('redirect_uri', `${env.UI_BASE_URL}/passport/callback`);
      hydraAuthUrl.searchParams.append('state', crypto.randomUUID());

      const iframe = document.createElement('iframe');
      iframe.id = 'global-hydra-auth-frame';
      iframe.src = hydraAuthUrl.toString();
      iframe.style.display = 'none';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.position = 'absolute';

      document.body.appendChild(iframe);
    } else if (isAuthRoute) {
      setIsLoadingToken(false);
    }

    return () => window.removeEventListener('message', handleAuthMessage);
  }, [isTokenLoaded, env, pathname]);

  return (
    <PassportTokenContext.Provider value={{ isTokenLoaded, isLoadingToken }}>
      {children}
    </PassportTokenContext.Provider>
  );
}

export function usePassportToken() {
  const context = useContext(PassportTokenContext);
  if (!context) {
    throw new Error('usePassportToken must be executed within a PassportTokenProvider tree');
  }
  return context;
}
