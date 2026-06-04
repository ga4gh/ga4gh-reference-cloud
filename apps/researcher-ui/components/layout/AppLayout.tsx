import React from 'react';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import Footer from './Footer';

interface AppLayoutProps {
  children: React.ReactNode
}

const AppLayout = ({children}: AppLayoutProps) => {
  return (
    <>
      <Navbar />
      <Sidebar>
        {children}
      </Sidebar>
      <Footer />
    </>
  )
}

export default AppLayout;
