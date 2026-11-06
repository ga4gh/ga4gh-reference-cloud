'use client';

import { useEffect, useRef, useState } from 'react';
import DrsObjectDownloadButton from '@/components/drs/DrsObjectDownloadButton';

type DrsAccessMethod = {
  access_url?: { url: string }
}

export type DrsObject = {
  id: string
  access_methods?: DrsAccessMethod[]
  [field: string]: unknown
}

interface DrsObjectModalProps {
  // the modal is open while drsId is set
  drsId: string | null
  onClose: () => void
}

// extracts the object ID from a hostname-based DRS URI (drs://<host>/<id>)
export const drsIdFromUri = (drsUri: string): string | null => {
  const match = drsUri.match(/^drs:\/\/[^/]+\/(.+)$/);
  return match ? match[1] : null;
};

// shows a DRS object (GET /ga4gh/drs/v1/objects/{id} via the UI's DRS proxy) with a download button
export default function DrsObjectModal({ drsId, onClose }: DrsObjectModalProps) {
  const modalRef = useRef<HTMLDialogElement>(null);
  const [drsObject, setDrsObject] = useState<DrsObject | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!drsId) {
      modalRef.current?.close();
      return;
    }

    modalRef.current?.showModal();
    setDrsObject(null);
    setError(null);

    let isCurrent = true; // ignore a response for an object the user has since navigated away from
    const loadDrsObject = async () => {
      try {
        const response = await fetch(`/api/ga4gh/drs/v1/objects/${encodeURIComponent(drsId)}`);
        const body = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(`${response.status}: ${body?.msg ?? body?.message ?? response.statusText}`);
        }
        if (isCurrent) setDrsObject(body);
      } catch (err) {
        console.error(`Failed to fetch DRS object ${drsId}:`, err);
        if (isCurrent) setError(err instanceof Error ? err.message : 'Failed to load DRS object');
      }
    };

    loadDrsObject();
    return () => { isCurrent = false; };
  }, [drsId]);

  return (
    // onClose fires for the Close button and the Escape key alike
    <dialog ref={modalRef} className="modal" onClose={onClose}>
      <div className="modal-box w-11/12 max-w-7xl bg-neutral text-neutral-content">
        <h3 className="text-lg font-bold mb-4 break-all">Inspecting DRS Object with ID: {drsId}</h3>
        <div className="card bg-base-100 box-shadow-card">
          <div className="card-body">
            {error ? (
              <div role="alert" className="alert alert-error rounded-none"><span>{error}</span></div>
            ) : (
              <div className={`pl-0 w-full text-left bg-base-300 text-base-content max-h-[70vh] rounded-none overflow-auto ${drsObject ? '' : 'animate-pulse'}`}>
                <pre className="p-2"><code>{drsObject ? JSON.stringify(drsObject, null, 2) : 'Loading…'}</code></pre>
              </div>
            )}
          </div>
        </div>
        <div className="modal-action">
          <DrsObjectDownloadButton fileUrlString={drsObject?.access_methods?.[0]?.access_url?.url} />
          <button className="ga4gh-btn-dark aspect-auto mx-2" onClick={() => modalRef.current?.close()}>
            <span className="btn-text">Close</span>
          </button>
        </div>
      </div>
    </dialog>
  );
}
