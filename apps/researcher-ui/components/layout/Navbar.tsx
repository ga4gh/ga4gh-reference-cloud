import Link from "next/link";
import GA4GHLogoSVG from "./GA4GHLogoSVG";

const Navbar = () => {
  return (
    <div className="navbar bg-primary text-primary-content">
      <Link href="/datasets">
        <span className="btn btn-ghost text-xl no-animation">
          <GA4GHLogoSVG size="40" />
          GA4GH Reference Cloud
        </span>
      </Link>
    </div>
  )
}

export default Navbar;
