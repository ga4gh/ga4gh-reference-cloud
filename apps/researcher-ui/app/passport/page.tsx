import { Metadata } from 'next';
import { Suspense } from 'react';
import PassportClient from './PassportClient';
import AppLayout from '@/components/layout/AppLayout';

export const metadata: Metadata = {
  title: 'View Passport Token - GA4GH Reference Cloud',
  description: 'Manage and use your passport tokens to securely access sensitive data assets via standard GA4GH compliant APIs.',
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