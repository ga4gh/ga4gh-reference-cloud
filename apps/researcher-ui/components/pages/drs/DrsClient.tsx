'use client';

import { useState, useEffect } from 'react';
import DrsManifestTable from '@/components/pages/drs/DrsManifestTable';
import { usePassportToken } from '@/components/context/PassportTokenContext';
import { Dataset } from '../datasets/DatasetsClient';

export default function DrsClient() {
  const { isTokenLoaded } = usePassportToken(); 
  
  const [approvedDatasetMap, setApprovedDatasetMap] = useState<Record<string, Dataset>>({});
  const [selectedDatasetId, setSelectedDatasetId] = useState<string>("");

  useEffect(() => {
    if (!isTokenLoaded) return; 

    const fetchDatasets = async () => {
      try {
        const response = await fetch('/api/datasets');

        if (!response.ok) {
          throw new Error('datasets API response was not ok');
        }

        const result = await response.json();
        const resultFiltered = result.filter((dataset: Dataset) => dataset.visa?.assertion?.currentStatus === "Approved");
        const newApprovedDatasetMap = Object.fromEntries(
          resultFiltered.map((dataset: Dataset) => [dataset.id, dataset])
        );
        setApprovedDatasetMap(newApprovedDatasetMap);
      } catch (error) {
        console.error('Failed to fetch datasets inside DRS layout:', error);
      }
    };

    fetchDatasets();
  }, [isTokenLoaded]);

  return (
    <div className="mt-4">
      {isTokenLoaded ? (
        <>
          <select 
            className="ga4gh-select w-full max-w-xs" 
            defaultValue="Select Dataset" 
            onChange={e => setSelectedDatasetId(e.target.value)}
          >
            <option disabled value="Select Dataset">Select Dataset</option>
            {Object.keys(approvedDatasetMap).map((datasetId) => (
              <option key={datasetId} value={datasetId}>
                {approvedDatasetMap[datasetId].name}
              </option>
            ))}
          </select>
          
          {selectedDatasetId !== "" ? (
            <DrsManifestTable selectedDatasetId={selectedDatasetId} />
          ) : null}
        </>
      ) : (
        <div className="flex flex-col gap-4 animate-pulse">
          <div className="h-11.25 bg-base-300 w-full max-w-xs rounded-sm"></div>
          <div className="h-75 bg-base-200 w-full rounded-box mt-4"></div>
        </div>
      )}
    </div>
  );
}
