'use client';

import { useEffect, useState } from 'react';
import { Info } from 'lucide-react';
import DrsObjectModal from '@/components/drs/DrsObjectModal';
import RefgetSequenceModal from '@/components/refget/RefgetSequenceModal';
import { CellActions, TABLE_RENDERERS } from '@/components/pages/data-connect/tableRenderers';

type DataModelProperty = {
  description?: string
}

type TableDataResponse = {
  data_model: { properties: Record<string, DataModelProperty> }
  data: Record<string, unknown>[]
  // omitted entirely on the last page
  pagination?: { next_page_url?: string }
}

// a page the user has visited: its token (null for the first page) and the number of rows before it
type PageCursor = {
  token: string | null
  rowStart: number
}

interface DataConnectTableProps {
  tableName: string
  // the table type (e.g. drs_object), which selects the type-specific cell renderers
  tableType: string
}

// collapsed arrays and objects show this many characters of their one-line JSON
const JSON_PREVIEW_LENGTH = 40;

// the UI never follows next_page_url directly: it only takes the page_token and calls its own proxy
const pageTokenFrom = (nextPageUrl?: string): string | null => {
  if (!nextPageUrl) return null;
  try {
    return new URL(nextPageUrl).searchParams.get('page_token');
  } catch {
    return null;
  }
};

// only accept a response with the expected shape, so a bad backend response shows an error instead of crashing
const isTableDataResponse = (body: unknown): body is TableDataResponse => {
  const candidate = body as TableDataResponse | null;
  return !!candidate
    && Array.isArray(candidate.data)
    && typeof candidate.data_model?.properties === 'object'
    && candidate.data_model.properties !== null;
};

const JsonCell = ({ value }: { value: object }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const compact = JSON.stringify(value);

  // nothing to expand
  if (compact === '[]' || compact === '{}') {
    return <span className="font-mono">{compact}</span>;
  }

  const preview = compact.length > JSON_PREVIEW_LENGTH ? `${compact.slice(0, JSON_PREVIEW_LENGTH)}…` : compact;

  return (
    <div className="min-w-0">
      <button
        type="button"
        className="link link-primary font-mono text-left"
        aria-expanded={isExpanded}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        {isExpanded ? '▾' : '▸'} {isExpanded ? 'collapse' : preview}
      </button>
      {isExpanded ? (
        <pre className="mt-1 p-2 bg-base-300 text-base-content overflow-x-auto"><code>{JSON.stringify(value, null, 2)}</code></pre>
      ) : null}
    </div>
  );
};

const renderCell = (value: unknown) => {
  if (value === null || value === undefined) return null; // empty cell
  if (typeof value === 'object') return <JsonCell value={value} />;
  return String(value); // strings (including bigint and timestamp values), numbers and booleans
};

export default function DataConnectTable({ tableName, tableType }: DataConnectTableProps) {
  // the last cursor is the current page; Previous pops it
  const [cursors, setCursors] = useState<PageCursor[]>([{ token: null, rowStart: 0 }]);
  const [result, setResult] = useState<TableDataResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // the object or sequence shown in a modal, if any
  const [modalDrsId, setModalDrsId] = useState<string | null>(null);
  const [modalSequenceId, setModalSequenceId] = useState<string | null>(null);

  const renderers = TABLE_RENDERERS[tableType] ?? {};
  const actions: CellActions = {
    openDrsObject: (drsId) => { setModalSequenceId(null); setModalDrsId(drsId); },
    openRefgetSequence: (sequenceId) => { setModalDrsId(null); setModalSequenceId(sequenceId); },
  };

  const renderColumnCell = (name: string, row: Record<string, unknown>) =>
    renderers[name]?.(row[name], row, actions) ?? renderCell(row[name]);

  const current = cursors[cursors.length - 1];
  const pageNumber = cursors.length;

  useEffect(() => {
    const loadPage = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const query = current.token ? `?page_token=${encodeURIComponent(current.token)}` : '';
        const response = await fetch(`/api/data-connect/table/${encodeURIComponent(tableName)}/data${query}`);
        const body = await response.json().catch(() => null);

        if (!response.ok) {
          // Data Connect errors carry { errors: [{ title, detail }] }
          const detail = body?.errors?.[0]?.detail ?? body?.message ?? response.statusText;
          throw new Error(`${response.status}: ${detail}`);
        }
        if (!isTableDataResponse(body)) {
          throw new Error('unexpected response from Data Connect');
        }

        setResult(body);
      } catch (err) {
        console.error(`Failed to fetch Data Connect table ${tableName}:`, err);
        setResult(null); // drop the previous page, so Next can't follow its stale token
        setError(err instanceof Error ? err.message : 'Failed to load table data');
      } finally {
        setIsLoading(false);
      }
    };

    loadPage();
  }, [tableName, current]);

  const nextToken = pageTokenFrom(result?.pagination?.next_page_url);

  const goToNextPage = () => {
    if (!result || !nextToken) return;
    setCursors([...cursors, { token: nextToken, rowStart: current.rowStart + result.data.length }]);
  };

  const goToPreviousPage = () => {
    if (cursors.length > 1) setCursors(cursors.slice(0, -1));
  };

  // first load
  if (isLoading && !result && !error) {
    return <div className="h-75 bg-base-200 w-full rounded-box mt-8 animate-pulse"></div>;
  }

  const columns = result ? Object.entries(result.data_model.properties) : [];
  const rows = result?.data ?? [];

  return (
    <div className="w-full max-w-full min-w-0 mt-8 space-y-4">
      <div className="flex flex-col md:flex-row w-full justify-between items-center gap-4 p-4 bg-base-200 text-base-content rounded-none">
        <p className="text-sm font-semibold">
          {error ? '' : rows.length > 0 ? `Rows ${current.rowStart + 1}–${current.rowStart + rows.length}` : 'No rows'}
        </p>

        {/* forward-only paging: Data Connect gives no total count, so there is no page count or last page */}
        <div className="ga4gh-pagination">
          <p className="text-sm font-semibold mr-2 text-base-content">Page:</p>

          {/* previous page arrow */}
          <button className="ga4gh-pagination-arrow" aria-label="Previous page" onClick={goToPreviousPage} disabled={isLoading || pageNumber <= 1}>
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" className="w-4 h-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
            </svg>
          </button>

          {/* current page button */}
          <button className="ga4gh-pagination-item active">{pageNumber}</button>

          {/* next page arrow */}
          <button className="ga4gh-pagination-arrow" aria-label="Next page" onClick={goToNextPage} disabled={isLoading || !nextToken}>
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" className="w-4 h-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
            </svg>
          </button>
        </div>
      </div>

      {error ? (
        <div role="alert" className="alert alert-error rounded-none">
          <span>Could not load table <code>{tableName}</code>: {error}</span>
        </div>
      ) : rows.length === 0 ? (
        <div className="p-4 bg-base-200 text-base-content rounded-none">This table has no rows.</div>
      ) : (
        <div className={`w-full overflow-x-auto border border-base-300 rounded-none ${isLoading ? 'opacity-50' : ''}`} aria-busy={isLoading}>
          <table className="ga4gh-data-table table table-zebra table-xs w-full rounded-none">
            <thead>
              <tr>
                <th className="sticky top-0 left-0 z-30 bg-base-200 text-base-content shadow-[2px_0_0_0_rgba(0,0,0,0.05)]"></th>
                {columns.map(([name, property]) => (
                  <th key={name} title={property.description} className="sticky top-0 z-10 bg-base-200 text-base-content text-xs">
                    <span className="inline-flex items-center gap-1">
                      {name}
                      {property.description ? <Info size={12} aria-label={property.description} /> : null}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={current.rowStart + i}>
                  <th data-label="Row" className={`sticky left-0 z-10 whitespace-nowrap ${i % 2 === 0 ? 'bg-base-100' : 'bg-base-200' } shadow-[2px_0_0_0_rgba(0,0,0,0.05)]`}>{current.rowStart + i + 1}</th>
                  {columns.map(([name]) => (
                    <td key={name} data-label={name}>{renderColumnCell(name, row)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <DrsObjectModal drsId={modalDrsId} onClose={() => setModalDrsId(null)} />
      <RefgetSequenceModal sequenceId={modalSequenceId} onClose={() => setModalSequenceId(null)} />
    </div>
  );
}
