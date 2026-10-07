import "../styles/globals.css"
import type { AppProps } from "next/app"
import { theme, globalStyles, ThemeProps } from "@ory/themes"
import { ToastContainer } from "react-toastify"
import "react-toastify/dist/ReactToastify.css"
import { ThemeProvider } from "styled-components"
import { createGlobalStyle } from "styled-components"
import { EnvProvider } from "@/context/EnvContext"

const GlobalStyle = createGlobalStyle((props: ThemeProps) =>
  globalStyles(props),
)

function MyApp({ Component, pageProps }: AppProps) {
  return (
    <html lang="en" data-theme="nord">
      <div data-testid="app-react">
        <EnvProvider>
          <ThemeProvider theme={theme}>
            <GlobalStyle />
            <Component {...pageProps} />
            <ToastContainer />
          </ThemeProvider>
        </EnvProvider>
      </div>
    </html>
  )
}

export default MyApp
