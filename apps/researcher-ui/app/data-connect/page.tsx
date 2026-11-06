import { Metadata } from 'next';
import { Suspense } from 'react';
import DataConnectClient from '@/components/pages/data-connect/DataConnectClient';
import AppLayout from '@/components/layout/AppLayout';

export const metadata: Metadata = {
  title: 'Explore Data - GA4GH Reference Cloud',
  description: 'Use the Data Connect API to browse data tables across datasets',
};

export default function DataConnectPage() {
  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Explore Data</h1>
      <h2 className="mb-4 text-2xl">Use the Data Connect API to browse data tables across datasets</h2>

      {/* DataConnectClient reads the selection from the URL (useSearchParams), which requires a Suspense boundary */}
      <Suspense fallback={
        <div className="flex flex-col gap-4 animate-pulse mt-4">
          <div className="h-11.25 bg-base-300 w-full max-w-xs rounded-sm"></div>
          <div className="h-75 bg-base-200 w-full rounded-box mt-4"></div>
        </div>
      }>
        <DataConnectClient />
      </Suspense>
    </AppLayout>
  );
}
