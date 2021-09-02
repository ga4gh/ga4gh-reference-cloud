/** @type {import('@docusaurus/types').DocusaurusConfig} */
module.exports = {
  title: 'GA4GH Starter Kit',
  tagline: 'Get up and running with GA4GH APIs',
  url: 'https://your-docusaurus-test-site.com',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/ga4gh.ico',
  organizationName: 'ga4gh', // Usually your GitHub org/user name.
  projectName: 'ga4gh-starter-kit-docs', // Usually your repo name.
  i18n: {
    defaultLocale: 'en',
    locales: [
      'en',
      'fr'
    ]
  },
  themeConfig: {
    navbar: {
      title: 'GA4GH Starter Kit',
      logo: {
        alt: 'GA4GH',
        src: 'img/ga4gh.svg',
      },
      items: [
        {
          type: 'doc',
          docId: 'getting-started/intro',
          position: 'left',
          label: 'Start Here'
        },
        {
          type: 'doc',
          docId: 'starter-kit-apis/overview',
          position: 'left',
          label: 'APIs'
        },
        {
          type: 'doc',
          docId: 'cookbooks/intro',
          position: 'left',
          label: 'Cookbooks'
        },
        {
          type: 'doc',
          docId: 'starter-kit-in-action/overview',
          position: 'left',
          label: 'In Action'
        },
        {
          type: 'localeDropdown',
          position: 'right'
        }//,
        // {
          // type: 'docsVersionDropdown',
          // position: 'right'
        // }
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Start Here',
              to: '/docs/getting-started/intro',
            },
            {
              label: 'APIs',
              to: '/docs/starter-kit-apis/overview'
            },
            {
              label: 'Cookbooks',
              to: '/docs/cookbooks/intro'
            },
            {
              label: 'In Action',
              to: '/docs/starter-kit-in-action/overview'
            }
          ],
        },
        {
          title: 'API Docs',
          items: [
            {
              label: 'DRS',
              to: '/docs/starter-kit-apis/drs/drs_overview'
            },
            {
              label: 'WES',
              to: '/docs/starter-kit-apis/wes/wes_overview'
            }
          ]
        },
        {
          title: 'Github',
          items: [
            {
              label: 'DRS',
              href: 'https://github.com/ga4gh/ga4gh-starter-kit-drs',
            },
            {
              label: 'WES',
              href: 'https://github.com/ga4gh/ga4gh-starter-kit-wes',
            }
          ],
        }
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Global Alliance for Genomics and Health.`,
    },
  },
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          editUrl:
            'https://github.com/facebook/docusaurus/edit/master/website/',
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
          editUrl:
            'https://github.com/facebook/docusaurus/edit/master/website/blog/',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
};
