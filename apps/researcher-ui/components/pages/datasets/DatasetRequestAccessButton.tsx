import React, {Dispatch, SetStateAction} from 'react';
import { Dataset } from 'pages/datasets';
import {useState} from 'react';

interface DatasetRequestAccessButtonProps {
  id: string
  datasetMap: Record<string, Dataset>
  setDatasetMap: Dispatch<SetStateAction<Record<string, Dataset>>>
}

const DatasetRequestAccessButton = ({id, datasetMap, setDatasetMap}: DatasetRequestAccessButtonProps) => {

  const [buttonDisabled, setButtonDisabled] = useState(false);

  const requestAccess = async (id: string) => {
    try {
      setButtonDisabled(true)
      setTimeout(() => setButtonDisabled(false), 3000)
      const response = await fetch(`/api/datasets/${id}/request-access`, {method: 'POST'})

      if (!response.ok) {
        throw new Error('dataset request access response was not ok')
      }

      const result = await response.json()
      const {...updatedDatasetMap} = datasetMap
      updatedDatasetMap[id] = result
      setDatasetMap(updatedDatasetMap);

    } catch (error) {
      console.error('Failed to request access to dataset', error)
    }
  }

  return (
    <>
      <div className="flex w-full flex-col">
        <div className="divider" />
      </div>
      <button
        onClick={() => requestAccess(id)}
        className="ga4gh-btn-dark aspect-auto"
        disabled={buttonDisabled}
      >
        <span className="btn-text">{buttonDisabled ? "Requesting Access ..." : "Request Access"}</span>
      </button>
    </>
  )
}

export default DatasetRequestAccessButton