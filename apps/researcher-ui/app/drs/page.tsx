import { Metadata } from 'next';
import { Suspense } from 'react';
import DrsClient from './DrsClient';

export const metadata: Metadata = {
  title: 'Data Repository Service (DRS) - GA4GH Reference Cloud',
  description: 'View and inspect DRS Objects using your GA4GH Passport token.',
};

export default function DrsPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading DRS Objects...</span>
      </div>
    }>
      <DrsClient />
    </Suspense>
  );
}
