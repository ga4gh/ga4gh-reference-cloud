import React from "react"
import Link from "next/link"
import styled from "styled-components"

interface CardProps {
  className?: string
  children?: React.ReactNode
}

const BaseCard = ({ className, children }: CardProps) => (
  <div className={`w-full max-w-2xl p-8 bg-white border border-brand-light shadow-card mx-auto ${className || ""}`}>
    {children}
  </div>
)

interface LinkButtonProps {
  className?: string
  href?: string
  disabled?: boolean
  onClick?: (e: React.MouseEvent) => void
  children?: React.ReactNode
  "data-testid"?: string
}

const BaseLinkButton = ({ className, href, disabled, onClick, children, "data-testid": testId }: LinkButtonProps) => {
  const buttonStyles = `ga4gh-btn-dark w-full !justify-start text-left px-6 py-3 transition-all ${
    disabled ? "opacity-50 cursor-not-allowed" : ""
  } ${className || ""}`

  if (href && !disabled) {
    return (
      <Link href={href} data-testid={testId} onClick={onClick} className={buttonStyles}>
        <span className="btn-text">{children}</span>
      </Link>
    )
  }

  return (
    <button type="button" data-testid={testId} disabled={disabled} onClick={onClick} className={buttonStyles}>
      <span className="btn-text">{children}</span>
    </button>
  )
}

export const typographyH2Styles = "text-xl font-bold font-heading text-brand-dark"
export const typographyLinkStyles = "text-brand-blue hover:underline cursor-pointer transition-colors"

export const MarginCard = styled(BaseCard)`
  margin-top: 4.5rem;
  margin-bottom: 1rem;
`

export const ActionCard = styled(BaseCard)`
  margin-bottom: 1rem;
`

export const CenterLink = styled.a`
  text-align: center;
  font-size: 15px;
  display: block;
  width: 100%;
  color: var(--color-brand-blue);
  font-family: var(--font-heading);
  font-weight: 700;
  
  &:hover {
    text-decoration: underline;
  }
`

export const TextLeftButton = styled(BaseLinkButton)`
  box-sizing: border-box;
`

export interface DocsButtonProps {
  title: string
  href?: string
  onClick?: () => void
  testid: string
  disabled?: boolean
  unresponsive?: boolean
}

const DocsButton = ({
  title,
  href,
  onClick,
  testid,
  disabled,
  unresponsive,
}: DocsButtonProps) => (
  <div className={`col-xs-4 ${!unresponsive ? "col-md-12" : ""}`}>
    <div className="box">
      <TextLeftButton
        onClick={onClick}
        disabled={disabled}
        data-testid={testid}
        href={href}
      >
        {title}
      </TextLeftButton>
    </div>
  </div>
)
