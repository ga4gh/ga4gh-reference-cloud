import React, { Dispatch, SetStateAction } from "react";
import { Dataset } from "./DatasetsClient";
import { ACCESS_STATUS_CLASSES, PassportVisaAssertionStatus } from "@/utils/passportVisa";
import DatasetRequestAccessButton from "./DatasetRequestAccessButton";

interface DatasetCardProps {
  id: string;
  title: string;
  description: string;
  tags: string[];
  isOpen: boolean;
  currentStatus: PassportVisaAssertionStatus;
  datasetMap: Record<string, Dataset>;
  setDatasetMap: Dispatch<SetStateAction<Record<string, Dataset>>>;
}

const DatasetCard = ({
  id,
  title,
  description,
  tags,
  isOpen,
  currentStatus,
  datasetMap,
  setDatasetMap,
}: DatasetCardProps) => {
  // open datasets have no visa, so they show a fixed badge instead of an access status
  const accessBadgeClasses = isOpen
    ? ACCESS_STATUS_CLASSES[PassportVisaAssertionStatus.Approved]
    : ACCESS_STATUS_CLASSES[currentStatus];
  const accessBadgeLabel = isOpen ? "Open Access" : currentStatus;

  return (
    <div className="card bg-base-100 w-96 box-shadow-card">
      <div className="card-body">
        <h2 className="card-title">{title}</h2>
        <p>{description}</p>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <div className="card-actions items-start">
          <div className="ga4gh-cta pt-1">Tags:</div>
          <div className="flex flex-1 flex-wrap gap-2">
            {tags.map((tag, index) => (
              <div className="ga4gh-tag" key={index}>
                {tag}
              </div>
            ))}
          </div>
        </div>
        <div className="card-actions items-center mt-4">
          <div className="ga4gh-cta">Access Status:</div>
          <div className={accessBadgeClasses}>{accessBadgeLabel}</div>
        </div>
        {!isOpen && currentStatus === PassportVisaAssertionStatus.NotRequested ? (
          <DatasetRequestAccessButton id={id} datasetMap={datasetMap} setDatasetMap={setDatasetMap} />
        ) : null}
      </div>
    </div>
  );
};

export default DatasetCard;
