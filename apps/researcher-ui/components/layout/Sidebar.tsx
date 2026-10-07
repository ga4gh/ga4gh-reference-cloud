import React, { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { LogoutLink } from "../../pkg"
import Link from 'next/link';
import { useEnv } from '@/context/EnvContext';

interface SidebarNavLinksProps {
  onNavigate?: () => void
}

type NavItem = {
  label: string
  href?: string
  external?: boolean
  disabled?: boolean
  onClick?: (event: React.MouseEvent<HTMLAnchorElement>) => void
}

type NavSection = {
  key: string
  title: string
  items: NavItem[]
}

// The nav items themselves, shared between the always-visible desktop
// sidebar (below) and the collapsible mobile dropdown panel rendered by
// Navbar below 768px width / on touch screens. Each of the 4 sections is
// its own collapsible submenu with a chevron, matching new_ga4gh's mobile
// nav (mobile-nav.js's menu-item-has-children accordion behavior).
export const SidebarNavLinks = ({ onNavigate }: SidebarNavLinksProps) => {
  const onLogout = LogoutLink()
  const env = useEnv()

  const [openSections, setOpenSections] = useState<Record<string, boolean>>({
    "data-access": true,
    "ga4gh-apis": true,
    "docs-guides": true,
    "settings": true,
  })

  const toggleSection = (key: string) => {
    setOpenSections((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  const sections: NavSection[] = [
    {
      key: "data-access",
      title: "Data Access",
      items: [
        { label: "Browse Datasets", href: "/datasets" },
        { label: "View Passport Token", href: "/passport" },
      ],
    },
    {
      key: "ga4gh-apis",
      title: "GA4GH APIs",
      items: [
        { label: "DRS", href: "/drs" },
        { label: "Beacon", disabled: true },
        { label: "htsget", disabled: true },
        { label: "refget", disabled: true },
      ],
    },
    {
      key: "docs-guides",
      title: "Docs & Guides",
      items: [
        { label: "Reference Cloud Docs", href: env.REFCLOUD_DOCS_URL, external: true },
        { label: "OpenAPI Reference", disabled: true },
      ],
    },
    {
      key: "settings",
      title: "Settings",
      items: [
        { label: "Manage Profile", disabled: true },
        { label: "Logout", href: "/", onClick: onLogout },
      ],
    },
  ]

  return (
    <ul className="anchor-menu-list menu bg-base-200 text-base-content min-h-full w-fit min-w-56 px-2" onClick={onNavigate}>
      {sections.map((section, index) => {
        const isOpen = openSections[section.key]
        return (
          <React.Fragment key={section.key}>
            <li>
              <button
                type="button"
                className="ga4gh-menu-section-toggle"
                aria-expanded={isOpen}
                onClick={(event) => {
                  event.stopPropagation()
                  toggleSection(section.key)
                }}
              >
                <h3 className="menu-title">{section.title}</h3>
                <ChevronDown size={18} className={`ga4gh-menu-chevron ${isOpen ? "ga4gh-menu-chevron--open" : ""}`} />
              </button>
              <ul className={`ga4gh-submenu ${isOpen ? "ga4gh-submenu--open" : ""}`}>
                {section.items.map((item) => (
                  <li key={item.label}>
                    {item.disabled ? (
                      <a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">{item.label}</a>
                    ) : item.external ? (
                      <a href={item.href} target="_blank" className="anchor-menu-link">{item.label}</a>
                    ) : item.onClick ? (
                      <a className="anchor-menu-link" href={item.href} onClick={item.onClick}>{item.label}</a>
                    ) : (
                      <Link href={item.href!}><span className="anchor-menu-link">{item.label}</span></Link>
                    )}
                  </li>
                ))}
              </ul>
            </li>
            {index < sections.length - 1 && (
              <li><div className="divider" /></li>
            )}
          </React.Fragment>
        )
      })}
    </ul>
  )
}

const Sidebar = () => {
  return (
    <aside className="ga4gh-sidebar w-fit min-w-56 px-2 flex-shrink-0 bg-base-200 text-base-content border-r border-base-300 p-4">
      <SidebarNavLinks />
    </aside>
  )
}

export default Sidebar;
