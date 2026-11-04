import styled from "styled-components"
import { NodeInputProps } from "./helpers"

export function getNodeLabel(node: any): string {
  if (node.meta?.label?.text) {
    return node.meta.label.text
  }

  return node.attributes?.name || ""
}

const CheckboxContainer = styled.div<{ $hasError?: boolean }>`
  display: flex;
  flex-direction: column;
  margin-bottom: 1rem;
  font-family: system-ui, -apple-system, sans-serif;

  .checkbox-label-wrapper {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    cursor: pointer;
    user-select: none;
    font-size: 0.95rem;
    color: ${(props) => (props.$hasError ? "#e53e3e" : "#2d3748")};

    &.disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }
  }

  input[type="checkbox"] {
    width: 1rem;
    height: 1rem;
    cursor: inherit;
    accent-color: ${(props) => (props.$hasError ? "#e53e3e" : "#3182ce")};
  }

  .subtitle {
    margin-top: 0.25rem;
    font-size: 0.8rem;
    white-space: pre-line;
    color: ${(props) => (props.$hasError ? "#e53e3e" : "#718096")};
  }
`

export function NodeInputCheckbox({
  node,
  attributes,
  setValue,
  disabled,
}: NodeInputProps) {
  const hasError = !!node.messages.find(({ type }) => type === "error")
  const isDisabled = attributes.disabled || disabled
  const subtitleText = node.messages.map(({ text }) => text).join("\n")

  return (
    <CheckboxContainer $hasError={hasError}>
      <label className={`checkbox-label-wrapper ${isDisabled ? "disabled" : ""}`}>
        <input
          type="checkbox"
          name={attributes.name}
          defaultChecked={attributes.value as boolean}
          onChange={(e) => setValue(e.target.checked)}
          disabled={isDisabled}
        />
        <span>{getNodeLabel(node)}</span>
      </label>
      
      {subtitleText && (
        <div className="subtitle">
          {subtitleText}
        </div>
      )}
    </CheckboxContainer>
  )
}
