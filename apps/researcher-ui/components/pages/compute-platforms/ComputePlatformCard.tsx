import React from "react";
import { ComputePlatform, ComputePlatformLimits } from "./ComputePlatformsClient";
import ComputePlatformRequestAccessButton from "./ComputePlatformRequestAccessButton";
import CopyableValue from "./CopyableValue";
import { ACCESS_STATUS_CLASSES, PassportVisaAssertionStatus, assertionStatus } from "@/utils/passportVisa";

interface ComputePlatformCardProps {
  platform: ComputePlatform;
  onPlatformChange: (platform: ComputePlatform) => void;
}

// e.g. 1800 -> "30 min", 7200 -> "2 h"
function formatDuration(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600} h`;
  if (seconds % 60 === 0) return `${seconds / 60} min`;
  return `${seconds} s`;
}

// shown in UTC, so the server and browser render the same text
function formatCheckedAt(checkedAt: string): string {
  return `${new Date(checkedAt).toISOString().slice(0, 16).replace("T", " ")} UTC`;
}

function limitLines(limits: ComputePlatformLimits): string[] {
  const perTask = [
    limits.maxTaskCpuCores === null ? null : `${limits.maxTaskCpuCores} CPU cores`,
    limits.maxTaskMemoryGb === null ? null : `${Number(limits.maxTaskMemoryGb)} GB memory`,
    limits.maxTaskDiskGb === null ? null : `${Number(limits.maxTaskDiskGb)} GB disk`,
  ].filter((part) => part !== null);
  return [
    limits.maxConcurrentRuns === null
      ? "Runs at a time: no per-user limit"
      : `Runs at a time: ${limits.maxConcurrentRuns} (queued or running)`,
    perTask.length === 0 ? "Per task: no limit" : `Per task: up to ${perTask.join(", ")}`,
    limits.maxRunDurationSeconds === null
      ? "Run duration: no limit"
      : `Run duration: up to ${formatDuration(limits.maxRunDurationSeconds)}`,
    limits.monthlyCoreHours === null
      ? "Monthly quota: no per-user quota"
      : `Monthly quota: ${Number(limits.monthlyCoreHours)} core-hours (CPU cores × hours)`,
  ];
}

const ComputePlatformCard = ({ platform, onPlatformChange }: ComputePlatformCardProps) => {
  const currentStatus = assertionStatus(platform.visa);
  const healthy = platform.health.status === "healthy";

  return (
    <div className="card bg-base-100 w-full max-w-2xl box-shadow-card">
      <div className="card-body">
        <h2 className="card-title">{platform.name}</h2>
        <p>{platform.description}</p>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>

        <div className="card-actions items-center">
          <div className="ga4gh-cta">Health:</div>
          <div
            className={healthy ? "ga4gh-status ga4gh-status--approved" : "ga4gh-status ga4gh-status--denied"}
            title={healthy ? undefined : "Runs can't be submitted until the platform is healthy again"}
          >
            {healthy ? "healthy" : "unhealthy"}
          </div>
          {platform.health.checkedAt ? (
            <span className="text-sm opacity-70">checked {formatCheckedAt(platform.health.checkedAt)}</span>
          ) : (
            <span className="text-sm opacity-70">not checked yet</span>
          )}
        </div>
        <div className="card-actions items-center">
          <div className="ga4gh-cta">Platform type:</div>
          <div className="ga4gh-tag">{platform.platformType ?? "not configured"}</div>
        </div>

        <div className="mt-2">
          <div className="ga4gh-cta">WES API</div>
          <CopyableValue value={platform.wesUrl} label="WES URL" />
          <div className="ga4gh-cta mt-2">TES API</div>
          <CopyableValue value={platform.tesUrl} label="TES URL" />
        </div>

        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <div className="ga4gh-cta">Workflows you can run</div>
        {platform.workflows.length === 0 ? (
          <p className="text-sm">No workflows are allowlisted on this platform yet.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {platform.workflows.map((workflow) => (
              <li key={workflow.workflowUrl}>
                <div className="font-bold">{workflow.name}</div>
                <div className="text-sm">{workflow.description}</div>
                <div className="text-sm opacity-70">
                  workflow_type {workflow.workflowType}, workflow_type_version {workflow.workflowTypeVersion},
                  workflow_url:
                </div>
                <CopyableValue value={workflow.workflowUrl} label={`workflow_url of ${workflow.name}`} />
              </li>
            ))}
          </ul>
        )}

        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <div className="ga4gh-cta">Your limits</div>
        <ul className="text-sm">
          {limitLines(platform.limits).map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>

        <div className="card-actions items-center mt-4">
          <div className="ga4gh-cta">Access Status:</div>
          <div className={ACCESS_STATUS_CLASSES[currentStatus]}>{currentStatus}</div>
        </div>
        {currentStatus === PassportVisaAssertionStatus.NotRequested ? (
          <ComputePlatformRequestAccessButton platform={platform} onPlatformChange={onPlatformChange} />
        ) : null}
      </div>
    </div>
  );
};

export default ComputePlatformCard;
