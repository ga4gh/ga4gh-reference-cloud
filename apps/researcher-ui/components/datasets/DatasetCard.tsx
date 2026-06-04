import React from 'react';

interface DatasetCardProps {
  title: string
  description: string
  tags: string[]
  apis: string[]
}

const DatasetCard = ({ title, description, tags, apis }: DatasetCardProps) => {
  return (
    <div className="card bg-base-100 w-96 shadow-xl">
      <div className="card-body">
        <h2 className="card-title">{title}</h2>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <p>{description}</p>
        <div className="flex w-full flex-col">
          <div className="divider" />
        </div>
        <div className="card-actions">
          <div>Tags:</div>
          {tags.map((tag, index) => (
            <div className="badge badge-outline" key={index}>
              {tag}
            </div>
          ))}
        </div>
        <div className="card-actions">
          <div>APIs:</div>
          {apis.map((api, index) => (
            <div className="badge badge-outline" key={index}>
              {api}
            </div>
          ))}
        </div>
        <div className="card-actions">
          <div>Access Status:</div>
          <div className="badge badge-outline">access not requested</div>
        </div>
        <button className="btn btn-secondary">Request Access</button>
      </div>
    </div>
  )
}

export default DatasetCard;
