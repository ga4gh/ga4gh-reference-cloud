import Link from "next/link";

const Navbar = () => {
  return (
    <div className="navbar bg-base-100 text-base-content box-shadow-base sticky top-0 z-[1000]">
      <Link href="/datasets">
        <span className="btn btn-ghost text-2xl no-animation">
          <img
            src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-mark-color.svg"
            alt="The Global Alliance for Genomics and Health"
            width="40"
            height="40"
          />
          <span className="text-primary">GA4GH</span>
          <span className="text-base-content">Reference Cloud</span>
        </span>
      </Link>
    </div>
  )
}

export default Navbar;
