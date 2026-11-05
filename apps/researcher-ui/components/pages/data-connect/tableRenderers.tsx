import { ReactNode } from "react";
import { drsIdFromUri } from "@/components/drs/DrsObjectModal";

// actions a renderer can trigger; DataConnectTable owns the modals they open
export type CellActions = {
  openDrsObject: (drsId: string) => void;
  openRefgetSequence: (sequenceId: string) => void;
};

// returns the cell's content, or undefined to fall back to the default rendering (e.g. for a null value)
type ColumnRenderer = (value: unknown, row: Record<string, unknown>, actions: CellActions) => ReactNode | undefined;

const CellLink = ({ label, onClick }: { label: string; onClick: () => void }) => (
  <button type="button" className="link link-primary cursor-pointer text-left break-all" onClick={onClick}>
    {label}
  </button>
);

const drsIdLink: ColumnRenderer = (value, _row, actions) =>
  typeof value === "string" ? <CellLink label={value} onClick={() => actions.openDrsObject(value)} /> : undefined;

const drsUriLink: ColumnRenderer = (value, _row, actions) => {
  if (typeof value !== "string") return undefined;
  const drsId = drsIdFromUri(value);
  return drsId ? <CellLink label={value} onClick={() => actions.openDrsObject(drsId)} /> : undefined;
};

const refgetSequenceLink: ColumnRenderer = (value, _row, actions) =>
  typeof value === "string" ? <CellLink label={value} onClick={() => actions.openRefgetSequence(value)} /> : undefined;

// per-table-type renderers, keyed by the table type from the dataset's tables list, then by column name.
// unknown table types and unlisted columns use the default rendering
export const TABLE_RENDERERS: Record<string, Record<string, ColumnRenderer>> = {
  drs_object: {
    id: drsIdLink,
    drs_uri: drsUriLink,
  },
  refget_sequence: {
    id: refgetSequenceLink,
    drs_uri: drsUriLink,
  },
  // datasets: no links
};
