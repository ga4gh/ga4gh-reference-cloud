import { UiNode, UiNodeAttributes } from "@ory/client";
import { NodeAnchor } from "./NodeAnchor";
import { NodeImage } from "./NodeImage";
import { NodeInput } from "./NodeInput";
import { NodeScript } from "./NodeScript";
import { NodeText } from "./NodeText";
import { FormDispatcher, NodeValue, ValueSetter } from "./helpers";

export function isUiNodeImageAttributes(
  attributes: UiNodeAttributes,
): attributes is Extract<UiNodeAttributes, { node_type: "img" }> {
  return (attributes && attributes.node_type === "img") || (attributes && "src" in attributes);
}

export function isUiNodeScriptAttributes(
  attributes: UiNodeAttributes,
): attributes is Extract<UiNodeAttributes, { node_type: "script" }> {
  return (attributes && attributes.node_type === "script") || (attributes && "async" in attributes);
}

export function isUiNodeTextAttributes(
  attributes: UiNodeAttributes,
): attributes is Extract<UiNodeAttributes, { node_type: "text" }> {
  return (attributes && attributes.node_type === "text") || (attributes && "text" in attributes);
}

export function isUiNodeAnchorAttributes(
  attributes: UiNodeAttributes,
): attributes is Extract<UiNodeAttributes, { node_type: "a" }> {
  return (attributes && attributes.node_type === "a") || (attributes && "href" in attributes);
}

export function isUiNodeInputAttributes(
  attributes: UiNodeAttributes,
): attributes is Extract<UiNodeAttributes, { node_type: "input" }> {
  return (
    (attributes && attributes.node_type === "input") || (attributes && "name" in attributes && "type" in attributes)
  );
}

interface Props {
  node: UiNode;
  disabled: boolean;
  value: NodeValue;
  setValue: ValueSetter;
  dispatchSubmit: FormDispatcher;
}

export const Node = ({ node, value, setValue, disabled, dispatchSubmit }: Props) => {
  if (isUiNodeImageAttributes(node.attributes)) {
    return <NodeImage node={node} attributes={node.attributes} />;
  }

  if (isUiNodeScriptAttributes(node.attributes)) {
    return <NodeScript node={node} attributes={node.attributes} />;
  }

  if (isUiNodeTextAttributes(node.attributes)) {
    return <NodeText node={node} attributes={node.attributes} />;
  }

  if (isUiNodeAnchorAttributes(node.attributes)) {
    return <NodeAnchor node={node} attributes={node.attributes} />;
  }

  if (isUiNodeInputAttributes(node.attributes)) {
    return (
      <NodeInput
        dispatchSubmit={dispatchSubmit}
        value={value}
        setValue={setValue}
        node={node}
        disabled={disabled}
        attributes={node.attributes}
      />
    );
  }

  return null;
};
