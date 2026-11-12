import { UiNode, UiNodeInputAttributes } from "@ory/client";
import { FormEvent, MouseEvent, useEffect } from "react";

// the value of a single form field in an ory flow
export type NodeValue = string | number | boolean | undefined;

export type ValueSetter = (value: NodeValue) => Promise<void>;

export type FormDispatcher = (e: FormEvent<HTMLFormElement> | MouseEvent) => Promise<void>;

export interface NodeInputProps {
  node: UiNode;
  attributes: UiNodeInputAttributes;
  value: NodeValue;
  disabled: boolean;
  dispatchSubmit: FormDispatcher;
  setValue: ValueSetter;
}

export const useOnload = (attributes: { onload?: string }) => {
  useEffect(() => {
    if (attributes.onload) {
      const intervalHandle = callWebauthnFunction(attributes.onload);

      return () => {
        window.clearInterval(intervalHandle);
      };
    }
  }, [attributes]);
};

export const callWebauthnFunction = (functionBody: string) => {
  const run = new Function(functionBody);

  const intervalHandle = window.setInterval(() => {
    if ((window as Window & { __oryWebAuthnInitialized?: boolean }).__oryWebAuthnInitialized) {
      run();
      window.clearInterval(intervalHandle);
    }
  }, 100);

  return intervalHandle;
};
