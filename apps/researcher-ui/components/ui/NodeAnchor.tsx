import { UiNode, UiNodeAnchorAttributes } from "@ory/client"

interface Props {
  node: UiNode
  attributes: UiNodeAnchorAttributes
}

const buttonStyle = {
  display: "inline-flex",
  alignItems: "center",
  justifyContent: "center",
  padding: "0.5rem 1rem",
  fontSize: "0.95rem",
  fontWeight: 500,
  borderRadius: "4px",
  border: "1px solid #d1d5db",
  backgroundColor: "#ffffff",
  color: "#374151",
  cursor: "pointer",
  transition: "background-color 0.2s, border-color 0.2s",
  width: "100%",
}

export const NodeAnchor = ({ node, attributes }: Props) => {
  return (
    <button
      style={buttonStyle}
      data-testid={`node/anchor/${attributes.id}`}
      onClick={(e) => {
        e.stopPropagation()
        e.preventDefault()
        window.location.href = attributes.href
      }}
    >
      {attributes.title.text}
    </button>
  )
}
