// app/passport/page.tsx
import { Metadata } from 'next';
import { Suspense } from 'react';
import PassportClient from './PassportClient';

export const metadata: Metadata = {
  title: 'View Passport Token - GA4GH Reference Cloud',
  description: 'Manage and use your passport tokens to securely access sensitive data assets via standard GA4GH compliant APIs.',
};

export default function PassportPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading passport token...</span>
      </div>
    }>
      <PassportClient />
    </Suspense>
  );
}
