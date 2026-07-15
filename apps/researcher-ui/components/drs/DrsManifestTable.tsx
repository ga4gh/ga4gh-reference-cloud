import React, {useState, useEffect } from 'react';

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

  useEffect(() => {
    const loadTableData = async () => {
      try {
        const response = await fetch(`/api/datasets/${selectedDatasetId}/manifests?page=${page}&size=${size}`)

        if (!response.ok) {
          throw new Error('datasets API response was not ok');
        }

        // update the table data
        const pageResult = await response.json()
        const newTableData = pageResult.content
        setTableData(newTableData)

        // grab the unique set of manifest key ids that appears in the dataset
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

        // update the final page number
        setFinalPage(pageResult.totalPages - 1)
      } catch (error) {
        console.error("Failed to fetch table data:", error)
      }
    }

    loadTableData();
  }, [selectedDatasetId, page, size])

  return (
    <>
      <div className="overflow-x-auto mt-8">
        <div className="flex w-full justify-between items-center p-4 bg-base-200 rounded-box">
          <div className="flex items-center gap-1">
            <p className="text-1xl">Results Per Page:</p>
            {sizeValues.map((sizeValue) => (
              sizeValue === size ? (
                <button className="btn btn-outline btn-active">{sizeValue}</button>
              ) : (
                <button className="btn btn-outline" onClick={() => changeSizeHandler(sizeValue)}>{sizeValue}</button>
              )
            ))}
          </div>
          <div className="flex items-center gap-1">
            <p className="text-1xl">Page:</p>

            {/* first page button if not on first page */}
            {page > 0 ? (
              <>
                <button className="btn btn-circle" onClick={() => setPage(page - 1)}>
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" className="w-6 h-6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
                  </svg>
                </button>
                <button className="btn btn-outline" onClick={() => setPage(0)}>1</button>
              </>
            ) : (
              <>
                <button className="btn btn-circle btn-disabled" aria-disabled="true">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" className="w-6 h-6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
                  </svg>
                </button>
              </>
            )}

            {page > 0 + 1 ? <p>...</p> : null}

            {/* current page button */}
            <button className="btn btn-outline btn-active">{page+1}</button>

            {page < finalPage - 1 ? <p>...</p> : null}

            {/* final page button if not on final page */}
            {page < finalPage ? (
              <>
                <button className="btn btn-outline" onClick={() => setPage(finalPage)}>{finalPage+1}</button>
                <button className="btn btn-circle" onClick={() => setPage(page + 1)}>
                  <svg xmlns="http://w3.org" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" className="w-6 h-6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                  </svg>
                </button>
              </>
            )  : (
              <>
                <button className="btn btn-circle btn-disabled" aria-disabled="true">
                  <svg xmlns="http://w3.org" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" className="w-6 h-6">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                  </svg>
                </button>
              </>
            )}
          </div>
        </div>
        <div>
          <table className="table table-zebra table-xs mt-4">
            {/* head */}
            <thead>
              <tr>
                <th></th>
                <th>Manifest DRS ID</th>
                <th>Manifest Description</th>
                {manifestSubFileTableKeysAndHeaders.map((tuple) => <th>{tuple[1]}</th> )}
              </tr>
            </thead>
            <tbody>
              {tableData.map((drsobject, i) => (
                <tr>
                  <th>{i}</th>
                  <td>{drsobject.id}</td>
                  <td>{drsobject.description}</td>
                  {manifestSubFileTableKeysAndHeaders.map((tuple) => <td>{drsobject.manifest_content[tuple[0]]}</td> )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}

export default DrsManifestTable;
