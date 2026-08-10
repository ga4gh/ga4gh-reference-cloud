import type { NextPage } from 'next'
import { useEnv } from '@/context/EnvContext'
import Link from 'next/link'

const HomeLoggedOut: NextPage = () => {
  const env = useEnv()

  return (
    <div>
      <div className="hero min-h-screen">
        <div className="ga4gh-hero-bg"></div>
        <div className="hero-overlay bg-[#363636]/60"></div>
        <div className="hero-content text-neutral-content justify-center items-stretch gap-x-12">
          <div className="f-logo max-w-md">
            <img
              src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-mark-color.svg"
              alt="The Global Alliance for Genomics and Health"
              width="100"
              height="100"
            />
            <h1 className="text-3xl font-bold text-white">Welcome to the GA4GH Reference Cloud</h1>
          </div>
          <div className="grid grid-cols-[auto_auto] content-between items-center gap-x-4 w-fit">
            <span className="text-right text-lg">Already registered?</span>
            <Link href="/login">
              <span className="ga4gh-btn-light">
                <span className="btn-text">Log in</span>
              </span>
            </Link>
            <span className="text-right text-lg">New user?</span>
            <Link href="/registration">
              <span className="ga4gh-btn-light">
                <span className="btn-text">Sign up</span>
              </span>
            </Link>
            <span className="text-right text-lg">Want to learn more about the platform?</span>
            <a className="ga4gh-btn-light" href={env.REFCLOUD_DOCS_URL}>
              <span className="btn-text">View docs</span>
            </a>
          </div>
        </div>
      </div>
    </div>  
  )
}

export default HomeLoggedOut
