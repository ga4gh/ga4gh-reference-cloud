/**
 * Swizzled from @docusaurus/theme-classic's Footer/Layout.
 *
 * Matches refcloud-ui's Footer.tsx: an `aside.f-logo` (logo + tagline +
 * copyright, stacked and left-aligned) as one flex sibling of the link
 * columns, justified space-between - instead of stock Docusaurus's
 * logo-and-copyright-centered-at-the-bottom layout.
 */
import React, {type ReactNode} from 'react';
import clsx from 'clsx';
import {ThemeClassNames} from '@docusaurus/theme-common';
import type {Props} from '@theme/Footer/Layout';

export default function FooterLayout({style, links, logo, copyright}: Props): ReactNode {
  return (
    <footer
      className={clsx(ThemeClassNames.layout.footer.container, 'footer', {
        'footer--dark': style === 'dark',
      })}>
      <div className="ga4gh-footer-container">
        <aside className="f-logo">
          {logo}
          <p className="f-logo-tagline">
            GA4GH Reference Cloud
            <br />
            Global Alliance for Genomics and Health
          </p>
          {copyright}
        </aside>
        {links}
      </div>
    </footer>
  );
}
