import React from 'react';
import { LogoutLink } from "../../pkg"
import Link from 'next/link';
import { useEnv } from '@/context/EnvContext';

const Sidebar = () => {
  const onLogout = LogoutLink()
  const env = useEnv()

  return (
    <aside className="w-fit min-w-56 px-2 flex-shrink-0 bg-base-200 text-base-content border-r border-base-300 p-4">
        <ul className="anchor-menu-list menu bg-base-200 text-base-content min-h-full w-fit min-w-56 px-2">
          <li><h3 className="menu-title">Data Access</h3></li>
          <li><Link href="/datasets"><span className="anchor-menu-link">Browse Datasets</span></Link></li>
          <li><Link href="/passport"><span className="anchor-menu-link">View Passport Token</span></Link></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li><h3 className="menu-title">GA4GH APIs</h3></li>
          <li><Link href="/drs"><span className="anchor-menu-link">DRS</span></Link></li>
          <li><a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">Beacon</a></li>
          <li><a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">htsget</a></li>
          <li><a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">refget</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li><h3 className="menu-title">Docs & Guides</h3></li>
          <li><a href={env.REFCLOUD_DOCS_URL} target="_blank" className="anchor-menu-link">Reference Cloud Docs</a></li>
          <li><a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">OpenAPI Reference</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li><h3 className="menu-title">Settings</h3></li>
          <li><a aria-disabled="true" className="anchor-menu-link pointer-events-none opacity-50" href="#">Manage Profile</a></li>
          <li><a className="anchor-menu-link" href="/" onClick={onLogout}>Logout</a></li>
        </ul>
    </aside>
  )
}

export default Sidebar;
