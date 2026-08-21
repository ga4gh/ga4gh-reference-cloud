import { Metadata } from "next";
import { Suspense } from "react";
import LoginClient from "./LoginClient";

export const metadata: Metadata = {
  title: "Log in",
  description: "Log in - GA4GH Reference Cloud",
};

export default function LoginPage() {
  return (
    <div className="hero min-h-screen">
      <div className="ga4gh-hero-bg"></div>
      <div className="hero-overlay bg-[#363636]/60"></div>
      <div className="hero-content flex-col lg:flex-row-reverse">
        <Suspense fallback={
          <div className="card bg-base-100 w-full max-w-sm shrink-0 box-shadow-card min-h-100 flex items-center justify-center">
            <div className="card-body flex flex-col items-center justify-center gap-4 w-full">
              <span className="loading loading-spinner loading-md text-brand-blue"></span>
              <p className="text-sm text-gray-500 font-sans tracking-wide animate-pulse">
                Loading login options...
              </p>
            </div>
          </div>
        }>
          <LoginClient />
        </Suspense>
      </div>
    </div>
  )
}
