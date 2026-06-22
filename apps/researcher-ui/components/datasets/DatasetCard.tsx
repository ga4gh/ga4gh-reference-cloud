import React, {Dispatch, SetStateAction} from 'react';
import { Dataset, PassportVisaAssertionStatus } from 'pages/datasets';
import DatasetRequestAccessButton from './DatasetRequestAccessButton';

interface DatasetCardProps {
  id: string
  title: string
  description: string
  tags: string[]
  currentStatus: PassportVisaAssertionStatus
  datasetMap: Record<string, Dataset>
  setDatasetMap: Dispatch<SetStateAction<Record<string, Dataset>>>
}

const DatasetCard = ({ id, title, description, tags, currentStatus, datasetMap, setDatasetMap}: DatasetCardProps) => {

  const accessBadgeClassesMap: Record<string, string> = {
    NotRequested: "badge badge-neutral",
    Requested: "badge badge-info",
    Approved: "badge badge-success",
    Denied: "badge badge-error",
    Revoked: "badge badge-error",
    Expired: "badge badge-error"
  };

  const accessBadgeClasses = accessBadgeClassesMap[currentStatus];

  return (
    <div className="card bg-base-100 w-96 shadow-xl">
      <div className="card-body">
        <h2 className="card-title">{title}</h2>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <p>{description}</p>
        <div className="card-actions">
          <div>Tags:</div>
          {tags.map((tag, index) => (
            <div className="badge badge-outline" key={index}>
              {tag}
            </div>
          ))}
        </div>
        <div className="card-actions">
          <div>Access Status:</div>
          <div className={accessBadgeClasses}>{currentStatus}</div>
        </div>
        {currentStatus === "NotRequested" ? <DatasetRequestAccessButton id={id} datasetMap={datasetMap} setDatasetMap={setDatasetMap} /> : null}
      </div>
    </div>
  )
}

export default DatasetCard;
