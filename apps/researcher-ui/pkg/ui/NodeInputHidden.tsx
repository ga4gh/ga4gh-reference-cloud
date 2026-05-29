import { NodeInputProps, useOnload } from "./helpers"

export function NodeInputHidden<T>({ attributes }: NodeInputProps) {
  useOnload(attributes as any)

  return (
    <>
    <h1>HELLO I AM A HIDDEN PROP</h1>
    <input
      type={attributes.type}
      name={attributes.name}
      value={attributes.value || "true"}
    />
    </>
  )
}
