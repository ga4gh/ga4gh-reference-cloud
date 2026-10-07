import GA4GHLogoSVG from "./GA4GHLogoSVG";

const Footer = () => {
  return (
    <footer className="footer footer-center bg-primary text-primary-content p-10">
      <aside>
        <GA4GHLogoSVG size="60" />
        <p className="font-bold">
          GA4GH Reference Cloud
          <br />
          Global Alliance for Genomics and Health
        </p>
        <p>Copyright © {new Date().getFullYear()}</p>
      </aside>
      <nav>
        <div className="grid grid-flow-col gap-4">
          <a href="https://x.com/ga4gh" target="_blank">
            <svg
              xmlns="http://w3.org"
              viewBox="0 0 24 24"
              width="24"
              height="24"
              className="fill-current"
            >
              <path
                d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z">
              </path>
            </svg>
          </a>
          <a href="https://www.youtube.com/c/GA4GH" target="_blank">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              className="fill-current">
              <path
                d="M19.615 3.184c-3.604-.246-11.631-.245-15.23 0-3.897.266-4.356 2.62-4.385 8.816.029 6.185.484 8.549 4.385 8.816 3.6.245 11.626.246 15.23 0 3.897-.266 4.356-2.62 4.385-8.816-.029-6.185-.484-8.549-4.385-8.816zm-10.615 12.816v-8l8 3.993-8 4.007z">
              </path>
            </svg>
          </a>
          <a href="https://www.facebook.com/GA4GH/" target="_blank">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              className="fill-current">
              <path
                d="M9 8h-3v4h3v12h5v-12h3.642l.358-4h-4v-1.667c0-.955.192-1.333 1.115-1.333h2.885v-5h-3.808c-3.596 0-5.192 1.583-5.192 4.615v3.385z">
              </path>
            </svg>
          </a>
        </div>
      </nav>
    </footer>
  )
}

export default Footer;