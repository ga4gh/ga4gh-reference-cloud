import { Metadata } from "next";
import { Suspense } from "react";
import RegistrationClient from "./RegistrationClient";

export const metadata: Metadata = {
  title: "Sign up",
  description: "Sign up - GA4GH Reference Cloud",
};

export default function RegistrationPage() {
  return (
    <Suspense fallback={
      <div className="hero min-h-screen flex items-center justify-center">
        <span className="loading loading-spinner loading-lg">Loading sign up context...</span>
      </div>
    }>
      <RegistrationClient />
    </Suspense>
  );
}
