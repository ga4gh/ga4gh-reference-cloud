"use client";

import { useRef, useState } from "react";

interface CancelRunButtonProps {
  // the run's WES proxy path: /api/ga4gh/wes/{platformId}/v1/runs/{runId}
  runPath: string;
  // called once the platform has accepted the cancellation
  onCancelled: () => void;
}

// cancels the run (WES POST /runs/{run_id}/cancel) after the researcher confirms
export default function CancelRunButton({ runPath, onCancelled }: CancelRunButtonProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const open = () => {
    setError(null);
    dialogRef.current?.showModal();
  };

  const cancel = async () => {
    setIsCancelling(true);
    setError(null);
    try {
      const response = await fetch(`${runPath}/cancel`, { method: "POST" });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.msg ?? body?.message ?? `the platform answered ${response.status}`);
      }
      dialogRef.current?.close();
      onCancelled();
    } catch (err) {
      setError(err instanceof Error ? err.message : "the platform couldn't be reached");
    }
    setIsCancelling(false);
  };

  return (
    <>
      <button type="button" className="ga4gh-btn-dark aspect-auto" onClick={open}>
        <span className="btn-text">Cancel run</span>
      </button>
      <dialog ref={dialogRef} className="modal" aria-labelledby="cancel-run-title">
        <div className="modal-box rounded-none">
          <h3 id="cancel-run-title" className="text-lg font-bold mb-4">
            Cancel this run?
          </h3>
          <p>Its tasks are stopped, and the run ends as Cancelled. A cancelled run can&apos;t be resumed.</p>
          {error ? (
            <div role="alert" className="alert alert-error mt-4 rounded-none">
              <span>Could not cancel the run: {error}.</span>
            </div>
          ) : null}
          <div className="modal-action">
            <button
              type="button"
              className="ga4gh-btn-dark aspect-auto"
              onClick={() => dialogRef.current?.close()}
              disabled={isCancelling}
            >
              <span className="btn-text">Keep running</span>
            </button>
            <button type="button" className="ga4gh-btn-dark aspect-auto" onClick={cancel} disabled={isCancelling}>
              <span className="btn-text">{isCancelling ? "Cancelling…" : "Cancel run"}</span>
            </button>
          </div>
        </div>
      </dialog>
    </>
  );
}
