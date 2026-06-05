import type { NextPage } from 'next'

const HomeLoggedOut: NextPage = () => {
  return (
    <div>
      <div
        className="hero min-h-screen"
        style={{
          backgroundImage: "url(https://img.daisyui.com/images/stock/photo-1507358522600-9f71e620c44e.webp)",
        }}>
        <div className="hero-overlay bg-opacity-60"></div>
        <div className="hero-content text-neutral-content text-center">
          <div className="max-w-md">
            <h1 className="mb-5 text-5xl font-bold">Welcome to the GA4GH Reference Cloud</h1>
            <div className="flex w-full flex-col">
              <div className="divider" />
            </div>
            <p>Already registered? <a className="link link-secondary" href="/login">Log in</a></p>
            <p>New user? <a className="link link-secondary" href="/registration">Sign up</a></p>
            <p>Want to learn more about the platform? <a className="link link-secondary" href={process.env.NEXT_PUBLIC_REFCLOUD_DOCS_URL}>View docs</a></p>
          </div>
        </div>
      </div>
    </div>  
  )
}

export default HomeLoggedOut