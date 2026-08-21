'use client';

import AppLayout from '@/components/layout/AppLayout';
import DatasetCard from '@/components/pages/datasets/DatasetCard';
import { useState, useEffect } from 'react';

export enum PassportVisaAssertionStatus {
  NotRequested = "NotRequested",
  Requested = "Requested",
  Approved = "Approved",
  Denied = "Denied",
  Revoked = "Revoked",
  Expired = "Expired"
}

export type Dataset = {
  id: string
  name: string
  description: string
  tags: string[]
  visa: {
    id: string
    name: string
    description: string
    assertion: {
      userId: string
      currentStatus: PassportVisaAssertionStatus
      currentStatusAt: string
    }
  }
}

interface DatasetsClientProps {
  initialDatasets: Dataset[];
}

export default function DatasetsClient({ initialDatasets }: DatasetsClientProps) {
  const [datasetMap, setDatasetMap] = useState<Record<string, Dataset>>(() => 
    Object.fromEntries(initialDatasets.map((dataset) => [dataset.id, dataset]))
  );

  const getValidAssertionStatus = (dataset: Dataset): PassportVisaAssertionStatus => {
    if (dataset.visa.assertion === null) {
      return PassportVisaAssertionStatus.NotRequested;
    }
    return dataset.visa.assertion.currentStatus;
  };

  useEffect(() => {
    const fetchDatasets = async () => {
      try {
        const response = await fetch('/api/datasets');
        if (!response.ok) throw new Error('datasets API response was not ok');
        
        const result = await response.json();
        const newDatasetMap = Object.fromEntries(
          result.map((dataset: Dataset) => [dataset.id, dataset])
        );
        setDatasetMap(newDatasetMap);
      } catch (error) {
        console.error('Failed to fetch datasets', error);
      }
    };

    if (initialDatasets.length === 0) {
      fetchDatasets();
    }
  }, [initialDatasets]);

  return (
    <AppLayout>
      <h1 className="mb-6 text-5xl font-bold">Browse Datasets</h1>
      <h2 className="mb-4 text-2xl">Explore and request access to the datasets available on the platform</h2>
      <div className="flex flex-wrap gap-8">
        {Object.keys(datasetMap).map((key, index) => (
          <DatasetCard
            key={index}
            id={datasetMap[key].id}
            title={datasetMap[key].name}
            description={datasetMap[key].description}
            tags={datasetMap[key].tags}
            currentStatus={getValidAssertionStatus(datasetMap[key])}
            datasetMap={datasetMap}
            setDatasetMap={setDatasetMap}
          />
        ))}
      </div>
    </AppLayout>
  );
}
