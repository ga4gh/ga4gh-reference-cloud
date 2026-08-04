import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'GA4GH Reference Cloud Docs',
  tagline: 'Reference implementations of GA4GH standards for cloud-based genomics',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the production url of your site here
  url: 'https://your-docusaurus-site.example.com',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'facebook', // Usually your GitHub org/user name.
  projectName: 'docusaurus', // Usually your repo name.

  onBrokenLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
          // Useful options to enforce blogging best practices
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    // Replace with your project's social card
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'GA4GH Reference Cloud Docs',
      logo: {
        alt: 'GA4GH Logo',
        src: 'img/logo-ga4gh.svg',
      },
      items: [
        {
          type: 'doc',
          docId: 'getting-started/start-here',
          position: 'left',
          label: 'Start Here'
        },
        {
          type: 'doc',
          docId: 'interactive-demos/intro-to-interactive-demos',
          position: 'left',
          label: 'Demos'
        },
        {
          type: 'doc',
          docId: 'deployment/deploying-the-reference-cloud',
          position: 'left',
          label: 'Deployment'
        },
        {
          type: 'doc',
          docId: 'api-reference/api-reference-overview',
          position: 'left',
          label: 'API Reference'
        },
        {
          href: 'https://refcloud.ga4gh.org',
          position: 'right',
          label: 'Go to App'
        },
        {
          href: 'https://github.com/ga4gh/ga4gh-reference-cloud',
          position: 'right',
          label: 'GitHub'
        },
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
              to: '/docs/getting-started/start-here',
            },
            {
              label: 'Demos',
              to: '/docs/interactive-demos/intro-to-interactive-demos',
            },
            {
              label: 'Deployment',
              to: '/docs/deployment/deploying-the-reference-cloud',
            },
            {
              label: 'API Reference',
              to: '/docs/api-reference/api-reference-overview',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'Go to App',
              href: 'https://refcloud.ga4gh.org',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/ga4gh/ga4gh-reference-cloud',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Global Alliance for Genomics and Health.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
