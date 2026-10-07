import { getNodeLabel } from "@ory/integrations/ui"
import { Button } from "@ory/themes"

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

  const buttonStyle = {
    display: "inline-block",
    padding: "14px 28px",
    fontSize: "1rem",
    fontWeight: "600",
    color: "#ffffff",
    background: "linear-gradient(135deg, #1b76bb 0%, #50aedb 100%)",
    border: "none",
    borderRadius: "12px",
    cursor: "pointer",
    boxShadow: "0 4px 14px rgba(27, 118, 187, 0.3)",
    transition: "all 0.2s cubic-bezier(0.4, 0, 0.2, 1)"
  }

  return (
    <>
      <div style={divStyle}>
        <button
          style={buttonStyle}
          name={attributes.name}
          value={attributes.value || ""}
          disabled={attributes.disabled || disabled}
        >
          {getNodeLabel(node)}
        </button>
      </div>
    </>
  )
}
