import { Metadata } from "next";
import { Suspense } from "react";
import LoginClient from "./LoginClient";

export const metadata: Metadata = {
  title: "Log in",
  description: "Log in - GA4GH Reference Cloud",
};

export default function LoginPage() {
  return (
    <Suspense fallback={
      <div className="hero min-h-screen flex items-center justify-center">
        <span className="loading loading-spinner loading-lg">Loading...</span>
      </div>
    }>
      <LoginClient />
    </Suspense>
  );
}
