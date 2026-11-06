'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { usePassportToken } from '@/components/context/PassportTokenContext';
import { Dataset, DatasetTable } from '@/components/pages/datasets/DatasetsClient';
import DrsManifestTable from '@/components/pages/data-connect/DrsManifestTable';
import DataConnectTable from '@/components/pages/data-connect/DataConnectTable';

// dropdown 1 entry: a dataset, or the platform-wide pseudo-dataset
type DatasetOption = {
  id: string
  name: string
  tables: DatasetTable[]
  hasManifests: boolean
}

// dropdown 2 entry: a Data Connect table, or the manifest view (backed by GET /datasets/{id}/manifests)
type TableOption =
  | { key: string, label: string, kind: 'data-connect', tableName: string, tableType: string }
  | { key: string, label: string, kind: 'manifests' }

// URL query parameters holding the selection, e.g. /data-connect?dataset=platform&table=datasets
const DATASET_PARAM = 'dataset';
const TABLE_PARAM = 'table';

// the platform-wide datasets table belongs to no dataset, so it is offered as its own entry
const PLATFORM_OPTION: DatasetOption = {
  id: 'platform',
  name: 'Platform',
  tables: [{ name: 'refcloud.platform.datasets', type: 'datasets' }],
  hasManifests: false,
};

// table option key for the manifest view. table types are snake case ids, so this cannot collide with one
const MANIFESTS_KEY = 'drs-manifests';

const KNOWN_TABLE_LABELS: Record<string, string> = {
  drs_object: 'DRS Objects',
  [MANIFESTS_KEY]: 'DRS IDs by Manifest',
  refget_sequence: 'Refget Sequences',
  datasets: 'Datasets',
};

// known options are listed in this order; unknown table types follow, sorted by label
const TABLE_OPTION_ORDER = ['drs_object', MANIFESTS_KEY, 'refget_sequence', 'datasets'];

// unknown table types are labelled from their id, e.g. foo_bar -> "Foo bar"
const labelForTableType = (type: string): string => {
  if (KNOWN_TABLE_LABELS[type]) return KNOWN_TABLE_LABELS[type];
  const words = type.replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
};

const buildTableOptions = (dataset: DatasetOption): TableOption[] => {
  const options: TableOption[] = dataset.tables.map((table) => ({
    key: table.type,
    label: labelForTableType(table.type),
    kind: 'data-connect',
    tableName: table.name,
    tableType: table.type,
  }));

  // the manifest view lists the dataset's DRS objects, so it is only offered alongside a readable drs_object table
  if (dataset.hasManifests && dataset.tables.some((table) => table.type === 'drs_object')) {
    options.push({ key: MANIFESTS_KEY, label: labelForTableType(MANIFESTS_KEY), kind: 'manifests' });
  }

  const rank = (key: string) => {
    const index = TABLE_OPTION_ORDER.indexOf(key);
    return index === -1 ? TABLE_OPTION_ORDER.length : index;
  };
  return options.sort((a, b) => rank(a.key) - rank(b.key) || a.label.localeCompare(b.label));
};

export default function DataConnectClient() {
  const { isTokenLoaded } = usePassportToken();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [isLoadingDatasets, setIsLoadingDatasets] = useState(true);
  const [datasetsError, setDatasetsError] = useState<string | null>(null);

  // datasets come from the Kratos session, so they can load before the passport token is ready
  useEffect(() => {
    const fetchDatasets = async () => {
      try {
        const response = await fetch('/api/datasets');
        if (!response.ok) {
          throw new Error(`datasets API responded with ${response.status}`);
        }
        const result = await response.json();
        setDatasets(Array.isArray(result) ? result : []);
      } catch (error) {
        console.error('Failed to fetch datasets for Data Connect page:', error);
        setDatasetsError('Could not load datasets. Please refresh the page to try again.');
      } finally {
        setIsLoadingDatasets(false);
      }
    };

    fetchDatasets();
  }, []);

  // platform first, then datasets with at least one table the user can read (open, or approved)
  const datasetOptions = useMemo<DatasetOption[]>(() => [
    PLATFORM_OPTION,
    ...datasets
      .filter((dataset) => (dataset.tables?.length ?? 0) > 0)
      .map((dataset) => ({
        id: dataset.id,
        name: dataset.name,
        tables: dataset.tables ?? [],
        hasManifests: dataset.hasManifests ?? false,
      })),
  ], [datasets]);

  // the selection lives in the URL so it survives a refresh and can be shared. a stale or
  // inaccessible id in the URL is ignored rather than shown as an empty selection
  const selectedDataset = datasetOptions.find((option) => option.id === searchParams.get(DATASET_PARAM));
  const tableOptions = useMemo(() => selectedDataset ? buildTableOptions(selectedDataset) : [], [selectedDataset]);
  const selectedTable = tableOptions.find((option) => option.key === searchParams.get(TABLE_PARAM))
    ?? (tableOptions.length === 1 ? tableOptions[0] : undefined); // a lone option is selected automatically

  const updateSelection = (datasetId: string, tableKey: string | null) => {
    const params = new URLSearchParams();
    params.set(DATASET_PARAM, datasetId);
    if (tableKey) params.set(TABLE_PARAM, tableKey);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  };

  if (isLoadingDatasets || !isTokenLoaded) {
    return (
      <div className="flex flex-col gap-4 animate-pulse mt-4">
        <div className="h-11.25 bg-base-300 w-full max-w-xs rounded-sm"></div>
        <div className="h-75 bg-base-200 w-full rounded-box mt-4"></div>
      </div>
    );
  }

  return (
    <div className="mt-4">
      {datasetsError ? (
        <div role="alert" className="alert alert-error mb-4 rounded-none">
          <span>{datasetsError}</span>
        </div>
      ) : null}

      <div className="flex flex-col md:flex-row gap-4">
        <select
          className="ga4gh-select w-full max-w-xs"
          aria-label="Dataset"
          value={selectedDataset?.id ?? ''}
          onChange={(e) => updateSelection(e.target.value, null)} // changing the dataset clears the table
        >
          <option disabled value="">Select Dataset</option>
          {datasetOptions.map((option) => (
            <option key={option.id} value={option.id}>{option.name}</option>
          ))}
        </select>

        <select
          className="ga4gh-select w-full max-w-xs"
          aria-label="Table"
          value={selectedTable?.key ?? ''}
          disabled={!selectedDataset}
          onChange={(e) => selectedDataset && updateSelection(selectedDataset.id, e.target.value)}
        >
          <option disabled value="">Select Table</option>
          {tableOptions.map((option) => (
            <option key={option.key} value={option.key}>{option.label}</option>
          ))}
        </select>
      </div>

      {/* keyed by the selection, so changing either dropdown remounts the view and resets its paging */}
      {selectedDataset && selectedTable ? (
        selectedTable.kind === 'manifests' ? (
          <DrsManifestTable key={`${selectedDataset.id}:${selectedTable.key}`} selectedDatasetId={selectedDataset.id} />
        ) : (
          <DataConnectTable key={`${selectedDataset.id}:${selectedTable.key}`} tableName={selectedTable.tableName} tableType={selectedTable.tableType} />
        )
      ) : null}
    </div>
  );
}
