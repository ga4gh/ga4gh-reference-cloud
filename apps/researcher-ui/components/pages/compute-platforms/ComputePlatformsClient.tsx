"use client";

import AppLayout from "@/components/layout/AppLayout";
import ComputePlatformCard from "@/components/pages/compute-platforms/ComputePlatformCard";
import { PassportVisa } from "@/utils/passportVisa";
import { useState, useEffect } from "react";

// an allowlisted workflow: a WES submission's workflow_url must match workflowUrl exactly
export type ComputePlatformWorkflow = {
  name: string;
  description: string;
  workflowUrl: string;
  workflowType: string;
  workflowTypeVersion: string;
};

// what one researcher may use. per-task resources and the run duration are always set (the platform's per-user limit
// or the infrastructure's ceiling); concurrent runs and monthly core-hours are null when there's no per-user limit
export type ComputePlatformLimits = {
  maxConcurrentRuns: number | null;
  maxTaskCpuCores: number | null;
  maxTaskMemoryGb: number | null;
  maxTaskDiskGb: number | null;
  maxRunDurationSeconds: number | null;
  monthlyCoreHours: number | null;
};

// core-api GET /compute-platforms. health has the same shape as in the platform's WES/TES service-info
export type ComputePlatform = {
  id: string;
  name: string;
  description: string;
  // e.g. nextflow-funnel-docker; null when the platform's connection isn't configured
  platformType: string | null;
  health: {
    status: "healthy" | "unhealthy";
    // absent until the first health check
    checkedAt?: string;
  };
  wesUrl: string;
  tesUrl: string;
  limits: ComputePlatformLimits;
  workflows: ComputePlatformWorkflow[];
  // every compute platform has a visa (there is no open-access compute)
  visa: PassportVisa;
};

interface ComputePlatformsClientProps {
  // null when the server-side fetch failed
  initialPlatforms: ComputePlatform[] | null;
}

export default function ComputePlatformsClient({ initialPlatforms }: ComputePlatformsClientProps) {
  const [platformMap, setPlatformMap] = useState<Record<string, ComputePlatform> | null>(() =>
    initialPlatforms === null ? null : Object.fromEntries(initialPlatforms.map((platform) => [platform.id, platform])),
  );

  useEffect(() => {
    const fetchPlatforms = async () => {
      try {
        const response = await fetch("/api/compute-platforms");
        if (!response.ok) throw new Error("compute platforms API response was not ok");

        const result = await response.json();
        if (!Array.isArray(result)) throw new Error("compute platforms API response was not a list");

        setPlatformMap(Object.fromEntries(result.map((platform: ComputePlatform) => [platform.id, platform])));
      } catch (error) {
        console.error("Failed to fetch compute platforms", error);
      }
    };

    if (initialPlatforms === null) {
      fetchPlatforms();
    }
  }, [initialPlatforms]);

  const platforms = platformMap === null ? [] : Object.values(platformMap);

  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Browse Compute Platforms</h1>
      <h2 className="mb-4 text-2xl">
        Request access to a compute platform, then run its workflows through its WES API, with DRS objects as inputs
      </h2>
      {platformMap !== null && platforms.length === 0 ? <p>No compute platforms are available yet.</p> : null}
      <div className="flex flex-wrap gap-8">
        {platforms.map((platform) => (
          <ComputePlatformCard
            key={platform.id}
            platform={platform}
            onPlatformChange={(updated) => setPlatformMap((current) => ({ ...current, [updated.id]: updated }))}
          />
        ))}
      </div>
    </AppLayout>
  );
}
