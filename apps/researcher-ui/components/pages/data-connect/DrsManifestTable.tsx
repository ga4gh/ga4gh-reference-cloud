import React, { useState, useEffect } from 'react';
import DrsObjectModal from '@/components/drs/DrsObjectModal';

// a manifest DRS object, as returned by core-api GET /datasets/{id}/manifests
type DrsObject = {
  id: string,
  name: string,
  size: number,
  created: string,
  updated: string,
  version: string,
  mime_type: string,
  description: string,
  manifest_content: Record<string, string>
}

interface DrsManifestTableProps {
  selectedDatasetId: string
}

const DrsManifestTable = ({selectedDatasetId}: DrsManifestTableProps) => {
  const [page, setPage] = useState(0)
  const [finalPage, setFinalPage] = useState(0)
  const [size, setSize] = useState(10)
  const [manifestSubFileTableKeysAndHeaders, setManifestSubFileTableKeysAndHeaders] = useState<[string, string][]>([])
  const [tableData, setTableData] = useState<DrsObject[]>([])
  // the sub-file shown in the DRS object modal, if any
  const [modalDrsId, setModalDrsId] = useState<string | null>(null);

  const sizeValues = [10, 25, 50, 100]

  const knownManifestKeysAndColumnHeaders = {
    "bam_file": "BAM File DRS ID",
    "bai_file": "BAI File DRS ID",
    "bas_file": "BAS File DRS ID",
    "cram_file": "CRAM File DRS ID",
    "crai_file": "CRAI File DRS ID",
    "csra_file": "CSRA File DRS ID"
  }

  const changeSizeHandler = (newSize: number) => {
    setSize(newSize)
    setPage(0)
  }

  const drsIdClickHandler = (drsId: string) => {
    setModalDrsId(drsId)
  }

  useEffect(() => {
    const loadTableData = async () => {
      try {
        const response = await fetch(`/api/datasets/${selectedDatasetId}/manifests?page=${page}&size=${size}`)

        if (!response.ok) {
          throw new Error('datasets API response was not ok');
        }

        const pageResult = await response.json()
        const newTableData = pageResult.content
        setTableData(newTableData)

        const uniqueManifestKeys = [...pageResult.content.reduce((set: Set<string>, obj: DrsObject) => {
          if (obj.manifest_content) {
            Object.keys(obj.manifest_content).forEach(key => set.add(key));
          }
          return set;
        }, new Set())];

        const newManifestSubFileTableKeysAndHeaders = [...uniqueManifestKeys].map((key: keyof typeof knownManifestKeysAndColumnHeaders): [string, string] => {
          if (Object.hasOwn(knownManifestKeysAndColumnHeaders, key)) {
            return [key, knownManifestKeysAndColumnHeaders[key]]
          } else {
            return [key, `${key} DRS ID`]
          }
        })

        setManifestSubFileTableKeysAndHeaders(newManifestSubFileTableKeysAndHeaders);

        setFinalPage(pageResult.totalPages - 1)
      } catch (error) {
        console.error("Failed to fetch table data:", error)
      }
    }

    loadTableData();
  }, [selectedDatasetId, page, size])

  return (
    <>
      <div className="w-full max-w-full min-w-0 mt-8 space-y-4">
        <div className="flex flex-col md:flex-row w-full justify-between items-center gap-4 p-4 bg-base-200 text-base-content rounded-none">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold">Results Per Page:</p>
            {sizeValues.map((sizeValue) => (
              sizeValue === size ? (
                <button key={sizeValue} className="border rounded-md btn btn-sm btn-neutral hover:border-neutral btn-active">{sizeValue}</button>
              ) : (
                <button key={sizeValue} className="border rounded-md btn btn-sm btn-outline btn-neutral hover:border-neutral" onClick={() => changeSizeHandler(sizeValue)}>{sizeValue}</button>
              )
            ))}
          </div>

          <div className="ga4gh-pagination">
            <p className="text-sm font-semibold mr-2 text-base-content">Page:</p>

            {/* previous page arrow */}
            <button className="ga4gh-pagination-arrow" onClick={() => setPage(page - 1)} disabled={page <= 0}>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" className="w-4 h-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
              </svg>
            </button>

            {page > 0 ? <button className="ga4gh-pagination-item" onClick={() => setPage(0)}>1</button> : null}

            {page > 1 ? <span className="ga4gh-pagination-item" style={{ cursor: 'default', opacity: 1 }}>...</span> : null}

            {/* current page button */}
            <button className="ga4gh-pagination-item active">{page+1}</button>

            {page < finalPage - 1 ? <span className="ga4gh-pagination-item" style={{ cursor: 'default', opacity: 1 }}>...</span> : null}

            {/* final page button if not on final page */}
            {page < finalPage ? <button className="ga4gh-pagination-item" onClick={() => setPage(finalPage)}>{finalPage+1}</button> : null}

            {/* next page arrow */}
            <button className="ga4gh-pagination-arrow" onClick={() => setPage(page + 1)} disabled={page >= finalPage}>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" className="w-4 h-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
              </svg>
            </button>
          </div>
        </div>

        <div className="w-full overflow-x-auto border border-base-300 rounded-none">
          <table className="ga4gh-data-table table table-zebra table-xs w-full rounded-none">
            <thead>
              <tr>
                <th className="sticky top-0 left-0 z-30 bg-base-200 text-base-content shadow-[2px_0_0_0_rgba(0,0,0,0.05)]"></th>
                <th className="sticky top-0 z-10 bg-base-200 text-base-content text-xs">Manifest DRS ID</th>
                <th className="sticky top-0 z-10 bg-base-200 text-base-content text-xs">Manifest Description</th>
                {manifestSubFileTableKeysAndHeaders.map((tuple, idx) => <th className="sticky top-0 z-10 bg-base-200 text-base-content text-xs" key={idx}>{tuple[1]}</th> )}
              </tr>
            </thead>
            <tbody>
              {tableData.map((drsobject, i) => (
                <tr key={drsobject.id || i}>
                  <th data-label="Row" className={`sticky left-0 z-10 whitespace-nowrap ${i % 2 === 0 ? 'bg-base-100' : 'bg-base-200' } shadow-[2px_0_0_0_rgba(0,0,0,0.05)]`}>{page * size + i + 1}</th>
                  <td data-label="Manifest DRS ID">{drsobject.id}</td>
                  <td data-label="Manifest Description">{drsobject.description}</td>
                  {manifestSubFileTableKeysAndHeaders.map((tuple, idx) => (
                    <td data-label={tuple[1]} key={idx}>
                      <span className="link link-primary cursor-pointer" onClick={() => drsIdClickHandler(drsobject.manifest_content[tuple[0]])} >
                        {drsobject.manifest_content[tuple[0]]}
                      </span>
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <DrsObjectModal drsId={modalDrsId} onClose={() => setModalDrsId(null)} />
      </div>
    </>
  )
}

export default DrsManifestTable;
