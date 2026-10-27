'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';

const EnvContext = createContext<Record<string, string>>({});

export function EnvProvider({ children }: { children: React.ReactNode }) {
  const [config, setConfig] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/config/env')
      .then((res) => res.json())
      .then((data) => {
        setConfig(data);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load runtime env:', err);
        setLoading(false);
      });
  }, []);

  return (
    <EnvContext.Provider value={config}>
      {!loading && children}
    </EnvContext.Provider>
  );
}

export const useEnv = () => useContext(EnvContext);