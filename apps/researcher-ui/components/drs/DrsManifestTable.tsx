import React, { useRef, useState, useEffect } from 'react';
import DrsObjectDownloadButton from './DrsObjectDownloadButton';

type DrsAccessUrl = {
  url: string
}

type DrsAccessMethod = {
  access_url: DrsAccessUrl
}

type DrsObject = {
  id: string,
  name: string,
  size: number,
  created: string,
  updated: string,
  version: string,
  mime_type: string,
  description: string,
  access_methods: DrsAccessMethod[],
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
  const [modalDrsId, setModalDrsId] = useState("");
  const [modalDrsObject, setModalDrsObject] = useState<DrsObject>();

  const modalRef = useRef<HTMLDialogElement>(null)

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

  const openModal = () => {
    modalRef.current?.showModal();
  }

  const closeModal = () => {
    modalRef.current?.close()
  }

  const drsIdClickHandler = (drsId: string) => {
    setModalDrsId(drsId)
    openModal()
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

  useEffect(() => {
    console.log("fetching data for DRS ID: " + modalDrsId);
    const loadDrsObject = async() => {
      try {
        const response = await fetch(`/api/ga4gh/drs/v1/objects/${modalDrsId}`)

        if (!response.ok) {
          throw new Error('DRS Object response was not ok');
        }

        const newDrsObject = await response.json()
        setModalDrsObject(newDrsObject);
      } catch (error) {
        console.error("Failed to fetch DRS Object:", error)
      }
    }

    loadDrsObject();
  }, [modalDrsId])

  return (
    <>
      <div className="w-full max-w-full min-w-0 mt-8 space-y-4">
        <div className="flex flex-col md:flex-row w-full justify-between items-center gap-4 p-4 bg-base-200 rounded-box">
          <div className="flex flex-wrap items-center gap-1">
            <p className="text-sm font-semibold">Results Per Page:</p>
            {sizeValues.map((sizeValue) => (
              sizeValue === size ? (
                <button key={sizeValue} className="btn btn-sm btn-outline btn-active">{sizeValue}</button>
              ) : (
                <button key={sizeValue} className="btn btn-sm btn-outline" onClick={() => changeSizeHandler(sizeValue)}>{sizeValue}</button>
              )
            ))}
          </div>

          <div className="flex flex-wrap items-center gap-1">
            <p className="text-sm font-semibold">Page:</p>

            {/* first page button if not on first page */}
            {page > 0 ? (
              <>
                <button className="btn btn-sm btn-circle" onClick={() => setPage(page - 1)}>
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
                  </svg>
                </button>
                <button className="btn btn-sm btn-outline" onClick={() => setPage(0)}>1</button>
              </>
            ) : (
              <>
                <button className="btn btn-sm btn-circle btn-disabled" aria-disabled="true">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
                  </svg>
                </button>
              </>
            )}

            {page > 1 ? <span className="px-1 text-sm">...</span> : null}

            {/* current page button */}
            <button className="btn btn-sm btn-outline btn-active">{page+1}</button>

            {page < finalPage - 1 ? <span className="px-1 text-sm">...</span> : null}

            {/* final page button if not on final page */}
            {page < finalPage ? (
              <>
                <button className="btn btn-sm btn-outline" onClick={() => setPage(finalPage)}>{finalPage+1}</button>
                <button className="btn btn-sm btn-circle" onClick={() => setPage(page + 1)}>
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                  </svg>
                </button>
              </>
            )  : (
              <>
                <button className="btn btn-sm btn-circle btn-disabled" aria-disabled="true">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                  </svg>
                </button>
              </>
            )}
          </div>
        </div>

        <div className="w-full max-h-[60vh] overflow-x-auto border border-base-300 rounded-box">
          <table className="table table-zebra table-xs whitespace-nowrap w-full">
            <thead>
              <tr>
                <th className="sticky top-0 left-0 z-30 bg-base-100 shadow-[2px_0_0_0_rgba(0,0,0,0.05)]"></th>
                <th className="sticky top-0 z-10 bg-base-100">Manifest DRS ID</th>
                <th className="sticky top-0 z-10 bg-base-100">Manifest Description</th>
                {manifestSubFileTableKeysAndHeaders.map((tuple, idx) => <th className="sticky top-0 z-10 bg-base-100" key={idx}>{tuple[1]}</th> )}
              </tr>
            </thead>
            <tbody>
              {tableData.map((drsobject, i) => (
                <tr key={drsobject.id || i}>
                  <th className={`sticky left-0 z-10 ${i % 2 === 0 ? 'bg-base-100' : 'bg-base-200' } shadow-[2px_0_0_0_rgba(0,0,0,0.05)]`}>{i}</th>
                  <td>{drsobject.id}</td>
                  <td>{drsobject.description}</td>
                  {manifestSubFileTableKeysAndHeaders.map((tuple, idx) => (
                    <td key={idx}>
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

        <dialog ref={modalRef} id="drs_object_modal" className="modal">
          <div className="modal-box w-11/12 max-w-7xl bg-neutral text-neutral-content">
            <h3 className="text-lg font-bold mb-4">Inspecting DRS Object with ID: {modalDrsId}</h3>
            <div className="card bg-base-100 shadow-xl">
              <div className="card-body">
                <div className="pl-0 w-full text-left bg-base-300 text-base-content max-h-[70vh] rounded-xl overflow-auto">
                  <pre className="p-2"><code>{JSON.stringify(modalDrsObject, null, 2)}</code></pre>
                </div>
              </div>
            </div>
            <div className="modal-action">
              <DrsObjectDownloadButton fileUrlString={modalDrsObject?.access_methods?.[0]?.access_url?.url} />
              <button className="mx-2 btn" onClick={closeModal}>Close</button>
            </div>
          </div>
        </dialog>
      </div>
    </>
  )
}

export default DrsManifestTable;
