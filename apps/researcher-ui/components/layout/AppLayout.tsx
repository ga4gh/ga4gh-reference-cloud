import React from 'react';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import Footer from './Footer';

interface AppLayoutProps {
  children: React.ReactNode
}

const AppLayout = ({children}: AppLayoutProps) => {
  return (
    <div className="bg-base-100 text-base-content min-h-screen flex flex-col">
      <Navbar />
      <div className="flex flex-col min-h-screen bg-base-100">
        <div className="flex flex-1 items-stretch bg-base-100">
          <Sidebar />
          <main className="flex-1 p-8 bg-base-100">
            {children}
          </main>
        </div>
        <Footer />
      </div>
    </div>
  )
}

export default AppLayout;
