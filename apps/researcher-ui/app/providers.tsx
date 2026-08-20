'use client';

import React from "react";
import { ThemeProvider, createGlobalStyle } from "styled-components"
import { EnvProvider } from "@/components/context/EnvContext"

interface ProvidersProps {
  children: React.ReactNode;
}

const defaultTheme = {
  colors: {
    primary: "#1a56db",
    background: "#ffffff",
    text: "#111827",
    border: "#e5e7eb",
  },
  fontFamily: "system-ui, -apple-system, sans-serif",
}

const GlobalStyle = createGlobalStyle`
  html, body {
    margin: 0;
    padding: 0;
    font-family: system-ui, -apple-system, sans-serif;
    background-color: #f9fafb;
    color: #111827;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }

  box-sizing-border-box, *, *::before, *::after {
    box-sizing: border-box;
  }
`

export function Providers({ children }: ProvidersProps) {
  return (
      <EnvProvider>
        <ThemeProvider theme={defaultTheme}>
          <GlobalStyle />
          {children}
        </ThemeProvider>
      </EnvProvider>
  );
}
