import { Metadata } from "next";
import { Suspense } from "react";
import SettingsClient from "@/components/pages/settings/SettingsClient";

export const metadata: Metadata = {
  title: "Profile Settings",
  description: "Manage your profile settings - GA4GH Reference Cloud",
};

export default function SettingsPage() {
  return (
    <Suspense fallback={
      <div className="p-8 flex items-center justify-center">
        <span className="loading loading-spinner loading-md">Loading settings...</span>
      </div>
    }>
      <SettingsClient />
    </Suspense>
  );
}
