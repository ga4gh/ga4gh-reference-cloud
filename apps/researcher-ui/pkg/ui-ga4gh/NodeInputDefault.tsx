import TextInput from './components/TextInput'

import { NodeInputProps } from "./helpers"

export function NodeInputDefault<T>(props: NodeInputProps) {
  const { node, attributes, value = "", setValue, disabled } = props

  // Some attributes have dynamic JavaScript - this is for example required for WebAuthn.
  const onClick = () => {
    // This section is only used for WebAuthn. The script is loaded via a <script> node
    // and the functions are available on the global window level. Unfortunately, there
    // is currently no better way than executing eval / function here at this moment.
    if (attributes.onclick) {
      const run = new Function(attributes.onclick)
      run()
    }
  }

  const inputGroupStyle = {
    gap: "8px",
    marginBottom: "20px",
    display: "grid"
  }

  const labelStyle = {
    fontSize: "0.9rem",
    fontWeight: "500",
    color: "#374151",
  }

  const inputStyle = {
    width: "100%",
    padding: "12px 16px",
    fontSize: "1rem",
    border: "1.5px solid #d1d5db", /* Light gray border */
    borderRadius: "8px", /* Smooth rounded corners */
    outline: "none",
    transition: "all 0.2s ease-in-out", /* Smooth transition for interaction */
  }

  const labelLookup: { [key: string]: string } = {
    "identifier": "Email",
    "password": "Password",
  }

  const placeholderLookup: { [key: string]: string } = {
    "identifier": "email",
    "password": "password",
  }

  // Render a generic text input field.
  return (
    <>
      <div style={inputGroupStyle}>
        <label className="label" style={labelStyle}>
          {labelLookup[attributes.name] || attributes.name}
        </label>
        <input
          style={inputStyle}
          title={node.meta.label?.text}
          onClick={onClick}
          onChange={(e) => {
            setValue(e.target.value)
          }}
          type={attributes.type}
          placeholder={placeholderLookup[attributes.name] || attributes.name}
          name={attributes.name}
          value={value}
          disabled={attributes.disabled || disabled}
          // help={node.messages.length > 0}
          // state={
          //   node.messages.find(({ type }) => type === "error") ? "error" : undefined
          // }
          // subtitle={
          //   <>
          //     {node.messages.map(({ text, id }, k) => (
          //       <span key={`${id}-${k}`} data-testid={`ui/message/${id}`}>
          //         {text}
          //       </span>
          //     ))}
          //   </>
          // }
        />
      </div>
    </>
  )
}
