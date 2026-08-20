import { UiNode, UiNodeTextAttributes, UiText } from "@ory/client"
import styled from "styled-components"

interface Props {
  node: UiNode
  attributes: UiNodeTextAttributes
}

// Replaces <CodeBox> and <ScrollableCodeBox>
const CustomCodeBox = styled.pre`
  background-color: #f4f5f7;
  border-radius: 4px;
  padding: 1rem;
  overflow-x: auto;
  font-family: monospace;
  font-size: 0.9rem;
  color: #333;
  margin: 0;

  code {
    white-space: pre;
    background: none;
    padding: 0;
    color: inherit;
  }
`

// Replaces <P> for the meta label text
const LabelText = styled.p`
  font-size: 0.95rem;
  color: #4a5568;
  margin-bottom: 0.5rem;
  font-weight: 500;
`

const Content = ({ node, attributes }: Props) => {
  switch (attributes.text.id) {
    case 1050015:
      // This text node contains lookup secrets. Let's make them a bit more beautiful!
      const secrets = (attributes.text.context as any).secrets.map(
        (text: UiText, k: number) => (
          <div
            key={k}
            data-testid={`node/text/${attributes.id}/lookup_secret`}
            className="col-xs-3"
          >
            {/* Used lookup_secret has ID 1050014 */}
            <code>{text.id === 1050014 ? "Used" : text.text}</code>
          </div>
        ),
      )
      return (
        <div
          className="container-fluid"
          data-testid={`node/text/${attributes.id}/text`}
        >
          <div className="row">{secrets}</div>
        </div>
      )
  }

  return (
    <div data-testid={`node/text/${attributes.id}/text`}>
      <CustomCodeBox>
        <code>{attributes.text.text}</code>
      </CustomCodeBox>
    </div>
  )
}

export const NodeText = ({ node, attributes }: Props) => {
  return (
    <>
      <LabelText data-testid={`node/text/${attributes.id}/label`}>
        {node.meta?.label?.text}
      </LabelText>
      <Content node={node} attributes={attributes} />
    </>
  )
}
