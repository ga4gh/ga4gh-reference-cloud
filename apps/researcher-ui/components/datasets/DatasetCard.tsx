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
    NotRequested: "ga4gh-status ga4gh-status--not-requested",
    Requested: "ga4gh-status ga4gh-status--requested",
    Approved: "ga4gh-status ga4gh-status--approved",
    Denied: "ga4gh-status ga4gh-status--denied",
    Revoked: "ga4gh-status ga4gh-status--denied",
    Expired: "ga4gh-status ga4gh-status--denied"
  };

  const accessBadgeClasses = accessBadgeClassesMap[currentStatus];

  return (
    <div className="card bg-base-100 w-96 box-shadow-card">
      <div className="card-body">
        <h2 className="card-title">{title}</h2>
        <p>{description}</p>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <div className="card-actions items-center">
          <div className="ga4gh-cta">Tags:</div>
          {tags.map((tag, index) => (
            <div className="ga4gh-tag" key={index}>
              {tag}
            </div>
          ))}
        </div>
        <div className="card-actions items-center mt-4">
          <div className="ga4gh-cta">Access Status:</div>
          <div className={accessBadgeClasses}>{currentStatus}</div>
        </div>
        {currentStatus === "NotRequested" ? <DatasetRequestAccessButton id={id} datasetMap={datasetMap} setDatasetMap={setDatasetMap} /> : null}
      </div>
    </div>
  )
}

export default DatasetCard;
