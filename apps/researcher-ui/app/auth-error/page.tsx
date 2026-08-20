// app/auth-error/page.tsx
import { Metadata } from "next";
import { Suspense } from "react";
import AuthErrorClient from "./AuthErrorClient";

export const metadata: Metadata = {
  title: "Authentication Error - GA4GH Reference Cloud",
  description: "An error occurred during your authentication sequence",
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
