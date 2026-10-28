/**
 * Swizzled from @docusaurus/theme-classic's Navbar/Logo.
 *
 * Matches refcloud-ui's Navbar.tsx title treatment: the brand name
 * split into "GA4GH" (text-primary) + the rest (base-content), rather
 * than one plain-colored string. Renders the logo image directly
 * instead of delegating to the shared @theme/Logo (which wraps its own
 * <Link>) so this title can sit alongside it inside a single link, the
 * same structure refcloud-ui uses.
 */
import React, {type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import {useThemeConfig} from '@docusaurus/theme-common';

export default function NavbarLogo(): ReactNode {
  const {
    navbar: {logo},
  } = useThemeConfig();
  const logoLink = useBaseUrl(logo?.href || '/');

  return (
    <Link to={logoLink} className="navbar__brand">
      {logo && (
        <img
          className="navbar__logo"
          src={useBaseUrl(logo.src)}
          alt={logo.alt}
          width={logo.width}
          height={logo.height}
        />
      )}
      <b className="navbar__title text--truncate">
        <span className="navbar__title-brand">GA4GH</span>
        <span className="navbar__title-rest">Reference Cloud Docs</span>
      </b>
    </Link>
  );
}
