import React, { useState } from "react";
import { ComputePlatform } from "./ComputePlatformsClient";

interface ComputePlatformRequestAccessButtonProps {
  platform: ComputePlatform;
  onPlatformChange: (platform: ComputePlatform) => void;
}

const ComputePlatformRequestAccessButton = ({
  platform,
  onPlatformChange,
}: ComputePlatformRequestAccessButtonProps) => {
  const [buttonDisabled, setButtonDisabled] = useState(false);

  const requestAccess = async () => {
    try {
      setButtonDisabled(true);
      setTimeout(() => setButtonDisabled(false), 3000);
      const response = await fetch(`/api/compute-platforms/${encodeURIComponent(platform.id)}/request-access`, {
        method: "POST",
      });

      if (!response.ok) {
        throw new Error("compute platform request access response was not ok");
      }

      // the platform with the user's (new or existing) access request
      const result: ComputePlatform = await response.json();
      onPlatformChange(result);
    } catch (error) {
      console.error("Failed to request access to compute platform", error);
    }
  };

  return (
    <>
      <div className="flex w-full flex-col">
        <div className="divider" />
      </div>
      <button onClick={requestAccess} className="ga4gh-btn-dark aspect-auto" disabled={buttonDisabled}>
        <span className="btn-text">{buttonDisabled ? "Requesting Access ..." : "Request Access"}</span>
      </button>
    </>
  );
};

export default ComputePlatformRequestAccessButton;
