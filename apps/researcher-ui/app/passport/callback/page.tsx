// app/passport/callback/page.tsx
'use client';

import { useEffect } from 'react';
import { useEnv } from '@/components/context/EnvContext';

export default function PassportCallbackPage() {
  const env = useEnv();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');

    if (code) {
      fetch('/api/oauth/token-exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code,
          redirect_uri: `${env.UI_BASE_URL}/passport/callback`
        })
      })
      .then((res) => res.json())
      .then((data) => {
        if (data.success) {
          // 💡 FIX: Change window.opener to window.parent because we are inside an iframe now
          if (window.parent) {
            window.parent.postMessage({ type: 'PASSPORT_AUTH_SUCCESS' }, window.location.origin);
          }
        }
      })
      .catch((err) => console.error("Callback network crash:", err));
    }
  }, [env]);

  return null; // Invisible component stream
}
