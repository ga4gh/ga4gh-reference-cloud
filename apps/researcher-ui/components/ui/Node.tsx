import { UiNode } from "@ory/client"
import { NodeAnchor } from "./NodeAnchor"
import { NodeImage } from "./NodeImage"
import { NodeInput } from "./NodeInput"
import { NodeScript } from "./NodeScript"
import { NodeText } from "./NodeText"
import { FormDispatcher, ValueSetter } from "./helpers"

export function isUiNodeImageAttributes(attributes: any): attributes is { src: string; id: string; width: number; height: number } {
  return attributes && attributes.node_type === "img" || (attributes && "src" in attributes)
}

export function isUiNodeScriptAttributes(attributes: any): attributes is { src: string; id: string; async: boolean; type: string } {
  return attributes && attributes.node_type === "script" || (attributes && "async" in attributes)
}

export function isUiNodeTextAttributes(attributes: any): attributes is { text: { id: number; text: string; context?: any }; id: string } {
  return attributes && attributes.node_type === "text" || (attributes && "text" in attributes)
}

export function isUiNodeAnchorAttributes(attributes: any): attributes is { href: string; id: string; title: { id: number; text: string } } {
  return attributes && attributes.node_type === "a" || (attributes && "href" in attributes)
}

export function isUiNodeInputAttributes(attributes: any): attributes is { name: string; type: string; value?: any; disabled: boolean } {
  return attributes && attributes.node_type === "input" || (attributes && "name" in attributes && "type" in attributes)
}

interface Props {
  node: UiNode
  disabled: boolean
  value: any
  setValue: ValueSetter
  dispatchSubmit: FormDispatcher
}

export const Node = ({
  node,
  value,
  setValue,
  disabled,
  dispatchSubmit,
}: Props) => {
  if (isUiNodeImageAttributes(node.attributes)) {
    return <NodeImage node={node} attributes={node.attributes as any} />
  }

  if (isUiNodeScriptAttributes(node.attributes)) {
    return <NodeScript node={node} attributes={node.attributes as any} />
  }

  if (isUiNodeTextAttributes(node.attributes)) {
    return <NodeText node={node} attributes={node.attributes as any} />
  }

  if (isUiNodeAnchorAttributes(node.attributes)) {
    return <NodeAnchor node={node} attributes={node.attributes as any} />
  }

  if (isUiNodeInputAttributes(node.attributes)) {
    return (
      <NodeInput
        dispatchSubmit={dispatchSubmit}
        value={value}
        setValue={setValue}
        node={node}
        disabled={disabled}
        attributes={node.attributes as any}
      />
    )
  }

  return null
}
