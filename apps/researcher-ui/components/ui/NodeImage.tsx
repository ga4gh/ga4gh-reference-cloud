import { UiNode, UiNodeImageAttributes } from "@ory/client";

interface Props {
  node: UiNode;
  attributes: UiNodeImageAttributes;
}

export const NodeImage = ({ node, attributes }: Props) => {
  // eslint-disable-next-line @next/next/no-img-element -- src is a kratos-supplied data uri (e.g. totp qr code)
  return <img data-testid={`node/image/${attributes.id}`} src={attributes.src} alt={node.meta.label?.text} />;
};
