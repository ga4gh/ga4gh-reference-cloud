'use client';

import { usePassportToken } from '@/components/context/PassportTokenContext';
import PassportTokenDisplay from '@/components/pages/passport/PassportTokenDisplay';

export default function PassportClient() {
  const { isTokenLoaded } = usePassportToken();

  return (
    <div className="mt-6">
      {isTokenLoaded ? (
        <PassportTokenDisplay />
      ) : (
        <div className="flex flex-col gap-4">
          <h2 className="mb-4 text-xl font-medium flex items-center gap-2">
            <span className="loading loading-ring loading-md text-primary"></span>
            Synchronizing passport token context...
          </h2>
          <div className="h-50 bg-base-200 w-full max-w-4xl rounded-box animate-pulse" />
        </div>
      )}
    </div>
  );
}
