const Footer = () => {
  return (
    <footer className="ga4gh-footer bg-neutral text-neutral-content p-10">
      <div className="ga4gh-footer-container">
        <aside className="f-logo">
          <img
            src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-mark-white.svg"
            alt="The Global Alliance for Genomics and Health"
            width="60"
            height="60"
          />
          <p className="font-bold">
            GA4GH Reference Cloud
            <br />
            Global Alliance for Genomics and Health
          </p>
          <p>Copyright © {new Date().getFullYear()}</p>
        </aside>
        <nav className="f-social">
          <a className="ga4gh-btn-light" href="https://www.youtube.com/c/GA4GH" target="_blank">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path
                d="M19.615 3.184c-3.604-.246-11.631-.245-15.23 0-3.897.266-4.356 2.62-4.385 8.816.029 6.185.484 8.549 4.385 8.816 3.6.245 11.626.246 15.23 0 3.897-.266 4.356-2.62 4.385-8.816-.029-6.185-.484-8.549-4.385-8.816zm-10.615 12.816v-8l8 3.993-8 4.007z">
              </path>
            </svg>
            <span className="btn-text">YouTube</span>
          </a>
          <a className="ga4gh-btn-light" href="https://linkedin.com/company/ga4gh" target="_blank">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path
                d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z">
              </path>
            </svg>
            <span className="btn-text">LinkedIn</span>
          </a>
          <a className="ga4gh-btn-light" href="https://www.facebook.com/GA4GH/" target="_blank">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
              <path
                d="M9 8h-3v4h3v12h5v-12h3.642l.358-4h-4v-1.667c0-.955.192-1.333 1.115-1.333h2.885v-5h-3.808c-3.596 0-5.192 1.583-5.192 4.615v3.385z">
              </path>
            </svg>
            <span className="btn-text">Facebook</span>
          </a>
        </nav>
      </div>
    </footer>
  )
}

export default Footer;