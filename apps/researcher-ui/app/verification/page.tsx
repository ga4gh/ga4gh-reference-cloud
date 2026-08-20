import { Metadata } from "next";
import { Suspense } from "react";
import VerificationClient from "./VerificationClient";

export const metadata: Metadata = {
  title: "Verify your account",
  description: "Verifiy your account - GA4GH Reference Cloud",
};

export default function VerificationPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading verification...</span>
      </div>
    }>
      <VerificationClient />
    </Suspense>
  );
}
