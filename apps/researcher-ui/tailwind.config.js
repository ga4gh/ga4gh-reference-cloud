/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx}",
    "./components/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
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
          'primary': '#1b76bb',
          'primary-content': '#ffffff',
          'secondary': '#50aedb',
          'secondary-content': '#ffffff',
          'accent': '#f9a434',
          'accent-content': '#ffffff',
          'neutral': '#2b323c',
          'neutral-content': '#ffffff',
          'base-100': '#fcfcfc',
          'base-200': '#f1f3f5',
          'base-300': '#e5e8eb',
          'base-content': '#2b323c',
          'info': '#46ade3',
          'success': '#2ab47a',
          'warning': '#ccae1d',
          'error': '#ee3a55',
        }
      }
    ],
  },
}
