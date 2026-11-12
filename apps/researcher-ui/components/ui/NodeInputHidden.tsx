import { NodeInputProps, useOnload } from "./helpers";

export function NodeInputHidden({ attributes }: NodeInputProps) {
  useOnload(attributes);

  return (
    <>
      <input type={attributes.type} name={attributes.name} value={attributes.value || "true"} />
    </>
  );
}
