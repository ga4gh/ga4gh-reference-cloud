"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

type RefgetMetadataResponse = {
  metadata: {
    md5: string | null;
    trunc512: string | null;
    ga4gh: string | null;
    length: number;
    aliases: { alias: string; naming_authority: string }[];
  };
};

interface RefgetSequenceModalProps {
  // the modal is open while sequenceId is set
  sequenceId: string | null;
  onClose: () => void;
}

// the first slice is fetched automatically when the modal opens, so sequences of any size open quickly
const DEFAULT_SLICE_LENGTH = 1000;

const errorMessage = async (response: Response): Promise<string> => {
  const body = await response.json().catch(() => null);
  return `${response.status}: ${body?.msg ?? body?.message ?? response.statusText}`;
};

// shows refget sequence metadata (GET /sequence/{id}/metadata) and lets the user fetch a start/end
// slice of the sequence (GET /sequence/{id}?start=&end=), both via the UI's refget proxy
export default function RefgetSequenceModal({ sequenceId, onClose }: RefgetSequenceModalProps) {
  const modalRef = useRef<HTMLDialogElement>(null);
  const [metadata, setMetadata] = useState<RefgetMetadataResponse | null>(null);
  const [metadataError, setMetadataError] = useState<string | null>(null);

  // the inputs hold strings so they can be cleared while typing; they are validated on fetch
  const [startInput, setStartInput] = useState("0");
  const [endInput, setEndInput] = useState("");
  const [slice, setSlice] = useState<{ start: number; end: number; bases: string } | null>(null);
  const [sliceError, setSliceError] = useState<string | null>(null);
  const [isLoadingSlice, setIsLoadingSlice] = useState(false);

  const length = metadata?.metadata.length;

  // the sequence currently shown, so a slice that arrives after the user switched sequences is dropped
  const activeIdRef = useRef<string | null>(null);

  const fetchSlice = async (id: string, start: number, end: number) => {
    setIsLoadingSlice(true);
    setSliceError(null);
    try {
      const response = await fetch(`/api/ga4gh/refget/v2/sequence/${encodeURIComponent(id)}?start=${start}&end=${end}`);
      if (!response.ok) {
        throw new Error(await errorMessage(response));
      }
      const bases = await response.text();
      if (activeIdRef.current === id) setSlice({ start, end, bases });
    } catch (err) {
      console.error(`Failed to fetch refget sequence ${id}:`, err);
      if (activeIdRef.current === id) {
        setSlice(null);
        setSliceError(err instanceof Error ? err.message : "Failed to load sequence");
      }
    } finally {
      if (activeIdRef.current === id) setIsLoadingSlice(false);
    }
  };

  // clear the previous sequence as soon as a different one is requested. adjusted during render rather than in the
  // effect, see https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes
  const [shownSequenceId, setShownSequenceId] = useState(sequenceId);
  if (sequenceId !== shownSequenceId) {
    setShownSequenceId(sequenceId);
    setMetadata(null);
    setMetadataError(null);
    setSlice(null);
    setSliceError(null);
    setIsLoadingSlice(false);
  }

  useEffect(() => {
    activeIdRef.current = sequenceId;
    if (!sequenceId) {
      modalRef.current?.close();
      return;
    }

    modalRef.current?.showModal();

    let isCurrent = true; // ignore a response for a sequence the user has since navigated away from
    const loadMetadata = async () => {
      try {
        const response = await fetch(`/api/ga4gh/refget/v2/sequence/${encodeURIComponent(sequenceId)}/metadata`);
        if (!response.ok) {
          throw new Error(await errorMessage(response));
        }
        const body: RefgetMetadataResponse = await response.json();
        if (!isCurrent) return;

        const end = Math.min(DEFAULT_SLICE_LENGTH, body.metadata.length);
        setMetadata(body);
        setStartInput("0");
        setEndInput(String(end));
        if (end > 0) fetchSlice(sequenceId, 0, end);
      } catch (err) {
        console.error(`Failed to fetch refget metadata for ${sequenceId}:`, err);
        if (isCurrent) setMetadataError(err instanceof Error ? err.message : "Failed to load sequence metadata");
      }
    };

    loadMetadata();
    return () => {
      isCurrent = false;
    };
  }, [sequenceId]);

  const onSubmitSlice = (event: FormEvent) => {
    event.preventDefault();
    if (!sequenceId || length === undefined) return;

    // refget coordinates are 0-based and end-exclusive. circular sequences (start > end) are not supported
    const start = Number(startInput);
    const end = Number(endInput);
    if (!Number.isInteger(start) || !Number.isInteger(end) || startInput === "" || endInput === "") {
      setSliceError("Start and end must be whole numbers.");
      return;
    }
    if (start < 0 || end > length || start >= end) {
      setSliceError(`Start and end must satisfy 0 ≤ start < end ≤ ${length}.`);
      return;
    }

    fetchSlice(sequenceId, start, end);
  };

  return (
    // onClose fires for the Close button and the Escape key alike
    <dialog ref={modalRef} className="modal" onClose={onClose}>
      <div className="modal-box w-11/12 max-w-7xl bg-neutral text-neutral-content">
        <h3 className="text-lg font-bold mb-4 break-all">Inspecting Refget Sequence with ID: {sequenceId}</h3>
        <div className="card bg-base-100 box-shadow-card">
          <div className="card-body gap-4">
            {metadataError ? (
              <div role="alert" className="alert alert-error rounded-none">
                <span>{metadataError}</span>
              </div>
            ) : (
              <>
                <div
                  className={`pl-0 w-full text-left bg-base-300 text-base-content max-h-[40vh] rounded-none overflow-auto ${metadata ? "" : "animate-pulse"}`}
                >
                  <pre className="p-2">
                    <code>{metadata ? JSON.stringify(metadata, null, 2) : "Loading…"}</code>
                  </pre>
                </div>

                {metadata ? (
                  <>
                    <form className="flex flex-col md:flex-row md:items-end gap-4" onSubmit={onSubmitSlice}>
                      <div className="flex flex-col gap-1">
                        <label className="ga4gh-label" htmlFor="refget-slice-start">
                          Start
                        </label>
                        <input
                          id="refget-slice-start"
                          className="ga4gh-input"
                          type="number"
                          min={0}
                          max={length}
                          step={1}
                          value={startInput}
                          onChange={(e) => setStartInput(e.target.value)}
                        />
                      </div>
                      <div className="flex flex-col gap-1">
                        <label className="ga4gh-label" htmlFor="refget-slice-end">
                          End
                        </label>
                        <input
                          id="refget-slice-end"
                          className="ga4gh-input"
                          type="number"
                          min={0}
                          max={length}
                          step={1}
                          value={endInput}
                          onChange={(e) => setEndInput(e.target.value)}
                        />
                      </div>
                      <button type="submit" className="ga4gh-btn-dark aspect-auto" disabled={isLoadingSlice}>
                        <span className="btn-text">{isLoadingSlice ? "Fetching…" : "Fetch"}</span>
                      </button>
                    </form>
                    <p className="text-sm text-base-content">
                      Positions are 0-based: start is included and end is excluded. Sequence length: {length} bases.
                    </p>

                    {sliceError ? (
                      <div role="alert" className="alert alert-error rounded-none">
                        <span>{sliceError}</span>
                      </div>
                    ) : slice ? (
                      <div className="flex flex-col gap-1">
                        <p className="text-sm font-semibold text-base-content">
                          Bases {slice.start}–{slice.end} ({slice.bases.length} bases)
                        </p>
                        <div
                          className={`w-full text-left bg-base-300 text-base-content max-h-[30vh] rounded-none overflow-auto ${isLoadingSlice ? "opacity-50" : ""}`}
                        >
                          <pre className="p-2 font-mono whitespace-pre-wrap break-all">
                            <code>{slice.bases}</code>
                          </pre>
                        </div>
                      </div>
                    ) : null}
                  </>
                ) : null}
              </>
            )}
          </div>
        </div>
        <div className="modal-action">
          <button className="ga4gh-btn-dark aspect-auto mx-2" onClick={() => modalRef.current?.close()}>
            <span className="btn-text">Close</span>
          </button>
        </div>
      </div>
    </dialog>
  );
}
