import { Metadata } from 'next';
import { Suspense } from 'react';
import DrsClient from '@/components/pages/drs/DrsClient';
import AppLayout from '@/components/layout/AppLayout';

export const metadata: Metadata = {
  title: 'Data Repository Service (DRS) - GA4GH Reference Cloud',
  description: 'View and Inspect DRS Objects',
};

export default function DrsPage() {
  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Data Repository Service (DRS)</h1>
      <h2 className="mb-4 text-2xl">View & Inspect DRS Objects</h2>
      
      <Suspense fallback={
        <div className="flex flex-col gap-4 animate-pulse mt-4">
          <div className="h-11.25 bg-base-300 w-full max-w-xs rounded-sm"></div>
          <div className="h-75 bg-base-200 w-full rounded-box mt-4"></div>
        </div>
      }>
        <DrsClient />
      </Suspense>
    </AppLayout>
  );
}
