import React from 'react';
import type { NextPage } from 'next'
import { LogoutLink } from "../../pkg"

interface SidebarProps {
  children: React.ReactNode
}

const Sidebar = ({children}: SidebarProps) => {

  const onLogout = LogoutLink()

  return (
    <div className="drawer lg:drawer-open">
      <input id="my-drawer-2" type="checkbox" className="drawer-toggle" />
      <div className="drawer-content flex flex-col p-8">
        {children}
        <label htmlFor="my-drawer-2" className="btn btn-primary drawer-button lg:hidden">
          Open drawer
        </label>
      </div>
      <div className="drawer-side h-full">
        <label htmlFor="my-drawer-2" aria-label="close sidebar" className="drawer-overlay"></label>
        <ul className="menu bg-base-200 text-base-content min-h-full w-80 p-4">
          {/* Sidebar content here */}
          <li className="menu-title">Data Access</li>
          <li><a className="link link-primary" href="/datasets">Browse Datasets</a></li>
          <li><a className="link link-primary" href="/passport">Configure Passport Token</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">GA4GH APIs</li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">DRS</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">Beacon</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">htsget</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">refget</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">Docs & Guides</li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">Reference Cloud Docs</a></li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">OpenAPI Reference</a></li>
          <div className="flex w-full flex-col">
            <div className="divider" />
          </div>
          <li className="menu-title">Settings</li>
          <li><a aria-disabled="true" className="link link-primary pointer-events-none opacity-50" href="#">Manage Profile</a></li>
          <li><a className="link link-primary" href="/" onClick={onLogout}>Logout</a></li>
        </ul>
      </div>
    </div>
  )
}

export default Sidebar;
