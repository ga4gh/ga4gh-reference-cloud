import { NodeInputProps } from "./helpers"

export function NodeInputDefault<T>(props: NodeInputProps) {
  const { node, attributes, value = "", setValue, disabled } = props

  const onClick = () => {
    if (attributes.onclick) {
      const run = new Function(attributes.onclick)
      run()
    }
  }

  const inputGroupStyle = {
    gap: "0.5rem",
    marginBottom: "1.5rem",
    display: "grid"
  }

  const labelLookup: { [key: string]: string } = {
    "identifier": "Email",
    "password": "Password",
    "traits.email": "Email",
    "traits.name.first": "First name",
    "traits.name.last": "Last name",
  }

  const placeholderLookup: { [key: string]: string } = {
    "identifier": "email",
    "password": "password",
    "traits.email": "email",
    "traits.name.first": "first name",
    "traits.name.last": "last name",
  }

  // Render a generic text input field.
  return (
    <>
      <div style={inputGroupStyle}>
        <label className="ga4gh-label">
          {labelLookup[attributes.name] || attributes.name}
        </label>
        <input
          className="ga4gh-input"
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
        />
      </div>
    </>
  )
}
