import type { NextPage } from 'next'
import AppLayout from 'components/layout/AppLayout';
import DatasetCard from 'components/datasets/DatasetCard';
import { useState, useEffect } from 'react';

enum PassportVisaAssertionStatus {
  NotRequested = "NotRequested",
  Requested = "Requested",
  Approved = "Approved",
  Denied = "Denied",
  Revoked = "Revoked",
  Expired = "Expired"
}

type Dataset = {
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

const Datasets: NextPage = () => {

  const [datasetMap, setDatasetMap] = useState<Record<string, Dataset>>({});

  const getValidAssertionStatus = (dataset: Dataset): PassportVisaAssertionStatus => {
    if (dataset.visa.assertion === null) {
      return PassportVisaAssertionStatus.NotRequested
    }
    return dataset.visa.assertion.currentStatus;
  };

  useEffect(() => {
    // fetch datasets from API
    const fetchDatasets = async () => {
      try {
        const response = await fetch('/api/datasets');

        if (!response.ok) {
          throw new Error('datasets API response was not ok');
        }

        const result = await response.json()
        const newDatasetMap = Object.fromEntries(
          result.map((dataset: Dataset) => [dataset.id, dataset]))
        setDatasetMap(newDatasetMap);

      } catch (error) {
        console.error('Failed to fetch datasets', error);
      }
    }

    fetchDatasets();
  }, [])

  return (
    <>
      <AppLayout>
        <h1 className="mb-5 text-5xl font-bold">Browse Datasets</h1>
        <h2 className="mb-4 text-2xl">Explore and request access to the datasets available on the platform</h2>
        <div className="flex flex-wrap m-16 gap-8">
          <input type="search" placeholder="Search..." className="input input-bordered w-full max-w-xs" />
        </div>
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
    </>
  )
}

export default Datasets

export type {
  Dataset,
  PassportVisaAssertionStatus
}