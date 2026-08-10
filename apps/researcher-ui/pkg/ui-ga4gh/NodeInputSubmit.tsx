import { getNodeLabel } from "@ory/integrations/ui"

import { NodeInputProps } from "./helpers"

export function NodeInputSubmit<T>({
  node,
  attributes,
  disabled,
}: NodeInputProps) {

  const divStyle = {
    display: "flex",
    justifyContent: "center",
    alignItems: "center"
  }

  return (
    <>
      <div style={divStyle}>
        <button
          className="ga4gh-btn-dark"
          name={attributes.name}
          value={attributes.value || ""}
          disabled={attributes.disabled || disabled}
        >
          <span className="btn-text">{getNodeLabel(node)}</span>
        </button>
      </div>
    </>
  )
}
