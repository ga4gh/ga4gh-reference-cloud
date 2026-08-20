import { callWebauthnFunction, NodeInputProps } from "./helpers"

export function getNodeLabel(node: any): string {
  if (node.meta?.label?.text) {
    return node.meta.label.text
  }

  return node.attributes?.name || "Submit"
}

// Inline styling config to replace Ory's standard action button primitive
const primaryButtonStyle = {
  display: "inline-flex",
  alignItems: "center",
  justifyContent: "center",
  padding: "0.625rem 1.25rem",
  fontSize: "0.95rem",
  fontWeight: 600,
  borderRadius: "6px",
  border: "1px solid #1a56db",
  backgroundColor: "#1a56db",
  color: "#ffffff",
  cursor: "pointer",
  transition: "all 0.15s ease-in-out",
  width: "100%",
  boxSizing: "border-box" as const,
}

export function NodeInputButton<T>({
  node,
  attributes,
  setValue,
  disabled,
  dispatchSubmit,
}: NodeInputProps) {
  const onClick = (e: React.MouseEvent | React.FormEvent<HTMLFormElement>) => {
    if (attributes.onclick) {
      e.stopPropagation()
      e.preventDefault()
      callWebauthnFunction(attributes.onclick)
      return
    }

    setValue(attributes.value).then(() => dispatchSubmit(e))
  }

  const isButtonDisabled = attributes.disabled || disabled

  return (
    <button
      type="button"
      name={attributes.name}
      onClick={(e) => {
        onClick(e)
      }}
      value={(attributes.value as string) || ""}
      disabled={isButtonDisabled}
      style={{
        ...primaryButtonStyle,
        ...(isButtonDisabled && {
          backgroundColor: "#e5e7eb",
          borderColor: "#e5e7eb",
          color: "#9ca3af",
          cursor: "not-allowed",
        }),
      }}
    >
      {getNodeLabel(node)}
    </button>
  )
}
