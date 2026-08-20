import { NodeInputProps } from "./helpers"

export function getNodeLabel(node: any): string {
  if (node.meta?.label?.text) {
    return node.meta.label.text
  }

  return node.attributes?.name || "Submit"
}

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
