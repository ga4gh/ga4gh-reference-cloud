import { Metadata } from "next";
import { Suspense } from "react";
import AuthErrorClient from "@/components/pages/auth-error/AuthErrorClient";

export const metadata: Metadata = {
  title: "Auth Error - GA4GH Reference Cloud",
  description: "An error occurred during your authentication/authorization sequence",
};

export default function AuthErrorPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading error context...</span>
      </div>
    }>
      <AuthErrorClient />
    </Suspense>
  );
}
