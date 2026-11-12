"use client";

import { Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { SidebarNavLinks } from "./Sidebar";

const Navbar = () => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const navRef = useRef<HTMLDivElement>(null);
  const pathname = usePathname();

  // Close on click/touch outside the navbar+panel, matching new_ga4gh's and
  // the analytics dashboard's mobile nav (click-outside-closes) behavior.
  useEffect(() => {
    if (!isMenuOpen) {
      return;
    }

    const onPointerDown = (event: PointerEvent) => {
      if (navRef.current && !navRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false);
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsMenuOpen(false);
      }
    };

    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [isMenuOpen]);

  // Navigating (including clicking a link inside the dropdown) closes it. Adjusted during render rather than
  // in an effect, see https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes
  const [menuPathname, setMenuPathname] = useState(pathname);
  if (pathname !== menuPathname) {
    setMenuPathname(pathname);
    setIsMenuOpen(false);
  }

  return (
    <div ref={navRef} className="ga4gh-navbar-container sticky top-0 z-[1000]">
      <div className="navbar bg-base-100 text-base-content box-shadow-base">
        <Link href="/datasets">
          <span className="ga4gh-navbar-brand btn btn-ghost no-animation">
            {/* eslint-disable-next-line @next/next/no-img-element -- static svg logo; next/image doesn't optimize svgs */}
            <img
              src="/logos/logo-mark-color.svg"
              alt="The Global Alliance for Genomics and Health"
              width="40"
              height="40"
            />
            <span className="text-primary">GA4GH</span>
            <span className="ga4gh-navbar-brand-full text-base-content">Reference Cloud</span>
          </span>
        </Link>
        <button
          type="button"
          className="ga4gh-navbar-mobile-trigger"
          aria-label={isMenuOpen ? "Close menu" : "Open menu"}
          aria-expanded={isMenuOpen}
          onClick={() => setIsMenuOpen((open) => !open)}
        >
          {isMenuOpen ? <X size={28} /> : <Menu size={28} />}
        </button>
      </div>
      <div className={`ga4gh-navbar-mobile-panel ${isMenuOpen ? "ga4gh-navbar-mobile-panel--open" : ""}`}>
        <SidebarNavLinks onNavigate={() => setIsMenuOpen(false)} />
      </div>
    </div>
  );
};

export default Navbar;
