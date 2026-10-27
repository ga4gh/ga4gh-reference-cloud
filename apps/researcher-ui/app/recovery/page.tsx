import { Metadata } from "next";
import { Suspense } from "react";
import RecoveryClient from "@/components/pages/recovery/RecoveryClient";

export const metadata: Metadata = {
  title: "Recover your account",
  description: "Recover your account - GA4GH Reference Cloud",
};

export default function RecoveryPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading recovery parameters...</span>
      </div>
    }>
      <RecoveryClient />
    </Suspense>
  );
}
