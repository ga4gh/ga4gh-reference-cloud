import type { NextPage } from 'next'
import { useEnv } from '@/context/EnvContext'
import Link from 'next/link'
import GA4GHLogoSVG from '../layout/GA4GHLogoSVG'

const HomeLoggedOut: NextPage = () => {
  const env = useEnv()

  return (
    <div>
      <div
        className="hero min-h-screen"
        style={{
          backgroundImage: "url(https://ga4gh-reference-cloud-public-assets.s3.us-east-2.amazonaws.com/images/gradient-geometric-background.webp)",
        }}>
        <div className="hero-overlay bg-opacity-60"></div>
        <div className="hero-content text-neutral-content text-center">
          <div className="max-w-md">
            <GA4GHLogoSVG size='100' />
            <h1 className="mb-5 text-5xl font-bold">Welcome to the GA4GH Reference Cloud</h1>
            <div className="flex w-full flex-col">
              <div className="divider" />
            </div>
            <p>Already registered? <Link href="/login"><span className="link link-accent">Log in</span></Link></p>
            <p>New user? <Link href="/registration"><span className="link link-accent">Sign up</span></Link></p>
            <p>Want to learn more about the platform? <a className="link link-accent" href={env.REFCLOUD_DOCS_URL}>View docs</a></p>
          </div>
        </div>
      </div>
    </div>  
  )
}

export default HomeLoggedOut
