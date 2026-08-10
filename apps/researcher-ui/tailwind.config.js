/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx}",
    "./components/**/*.{js,ts,jsx,tsx}",
    "./pkg/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Figtree', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        heading: ['"Work Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        label: ['"Space Grotesk"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
    require('daisyui'),
  ],

  daisyui: {
    themes: [
      "cupcake",
      "nord",
      {
        ga4gh: {
          ...require("daisyui/src/theming/themes")["nord"],
          'primary': '#1b75bb',
          'primary-content': '#ffffff',
          'secondary': '#4faedc',
          'secondary-content': '#ffffff',
          'accent': '#faa633',
          'accent-content': '#ffffff',
          'neutral': '#363636',
          'neutral-content': '#ffffff',
          'base-100': '#ffffff',
          'base-200': '#efefef',
          'base-300': '#dcdcdc',
          'base-content': '#363636',
          'info': '#4faedc',
          'success': '#8cc63e',
          'warning': '#faa633',
          'error': '#e34a3a',
          '--rounded-box': '1rem',
        }
      }
    ],
  },
}
