import { Metadata } from 'next';
import { Suspense } from 'react';
import PassportClient from '../../components/pages/passport/PassportClient';
import AppLayout from '@/components/layout/AppLayout';

export const metadata: Metadata = {
  title: 'View Passport Token - GA4GH Reference Cloud',
  description: 'Use your passport token to access data via GA4GH APIs',
};

export default function PassportPage() {
  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">View Passport Token</h1>
      <h2 className="mb-4 text-2xl">Use your passport token to access data via GA4GH APIs</h2>
      
      <Suspense fallback={<div className="h-20 bg-base-200 animate-pulse rounded-box" />}>
        <PassportClient />
      </Suspense>
    </AppLayout>
  );
}