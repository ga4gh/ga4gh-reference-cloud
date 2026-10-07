import React from 'react';
import { LogoutLink } from "../../pkg"
import Link from 'next/link';
import { useEnv } from '@/context/EnvContext';

const Sidebar = () => {
  const onLogout = LogoutLink()
  const env = useEnv()

  return (
    <aside className="w-fit min-w-56 px-1 flex-shrink-0 bg-base-200 text-base-content border-r border-base-300 p-4">
        <ul className="menu bg-base-200 text-base-content min-h-full w-fit min-w-56 px-1">
          <li className="menu-title">Data Access</li>
          <li><Link href="/datasets"><span className="link link-primary">Browse Datasets</span></Link></li>
          <li><Link href="/passport"><span className="link link-primary" >View Passport Token</span></Link></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">GA4GH APIs</li>
          <li><Link href="/drs"><span className="link link-primary">DRS</span></Link></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">Beacon</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">htsget</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">refget</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">Docs & Guides</li>
          <li><a href={env.REFCLOUD_DOCS_URL} target="_blank"><span className="link link-primary">Reference Cloud Docs</span></a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">OpenAPI Reference</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">Settings</li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">Manage Profile</a></li>
          <li><a className="link link-primary" href="/" onClick={onLogout}>Logout</a></li>
        </ul>
    </aside>
  )
}

export default Sidebar;
