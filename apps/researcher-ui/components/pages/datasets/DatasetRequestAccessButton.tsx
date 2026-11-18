import React, { Dispatch, SetStateAction } from "react";
import { Dataset } from "./DatasetsClient";
import { useState } from "react";

interface DatasetRequestAccessButtonProps {
  id: string;
  datasetMap: Record<string, Dataset>;
  setDatasetMap: Dispatch<SetStateAction<Record<string, Dataset>>>;
}

const DatasetRequestAccessButton = ({ id, datasetMap, setDatasetMap }: DatasetRequestAccessButtonProps) => {
  const [buttonDisabled, setButtonDisabled] = useState(false);

  const requestAccess = async (id: string) => {
    try {
      setButtonDisabled(true);
      setTimeout(() => setButtonDisabled(false), 3000);
      const response = await fetch(`/api/datasets/${id}/request-access`, { method: "POST" });

      if (!response.ok) {
        throw new Error("dataset request access response was not ok");
      }

      // the dataset with the user's (new or existing) access request. only its visa changes: keep the fields the
      // request endpoint doesn't return (tables, hasManifests)
      const result: Dataset = await response.json();
      setDatasetMap({ ...datasetMap, [id]: { ...datasetMap[id], visa: result.visa } });
    } catch (error) {
      console.error("Failed to request access to dataset", error);
    }
  };

  return (
    <>
      <div className="flex w-full flex-col">
        <div className="divider" />
      </div>
      <button onClick={() => requestAccess(id)} className="ga4gh-btn-dark aspect-auto" disabled={buttonDisabled}>
        <span className="btn-text">{buttonDisabled ? "Requesting Access ..." : "Request Access"}</span>
      </button>
    </>
  );
};

export default DatasetRequestAccessButton;
