import React, {useState, useEffect } from 'react';
import { Dataset, PassportVisaAssertionStatus } from '../../pages/datasets';

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
      } catch (error) {
        console.error("Failed to fetch table data:", error)
      }
    }

    loadTableData();
  }, [selectedDatasetId, page, size])

  return (
    <>
      <div className="overflow-x-auto mt-8">
        <div className="flex items-center gap-1">
          <p className="text-1xl">Results Per Page:</p>
          {sizeValues.map((sizeValue) => (
            sizeValue === size ? (
              <button className="btn btn-outline btn-active">{sizeValue}</button>
             ) : (
              <button className="btn btn-outline" onClick={() => setSize(sizeValue)}>{sizeValue}</button>
             )
          ))}
        </div>
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
    </>
  )
}

export default DrsManifestTable;
