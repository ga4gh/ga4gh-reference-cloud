import { UiText } from "@ory/client"
import { Alert, AlertContent } from "@ory/themes"

interface MessageProps {
  message: UiText
}

export const Message = ({ message }: MessageProps) => {
  const divStyle = {
    marginBottom: "0.5rem",
  }

  const messageStyle = {
    fontSize: "0.75rem",
    fontWeight: "500",
    color: "#374151",
  }

  return (
    <div style={divStyle}>
      <p data-testid={`ui/message/${message.id}`} style={messageStyle}>
        {message.text}
      </p>
    </div>
  )
}

interface MessagesProps {
  messages?: Array<UiText>
}

export const Messages = ({ messages }: MessagesProps) => {
  if (!messages) {
    // No messages? Do nothing.
    return null
  }

  return (
    <div>
      {messages.map((message) => (
        <Message key={message.id} message={message} />
      ))}
    </div>
  )
}
