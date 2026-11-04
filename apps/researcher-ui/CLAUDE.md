# Project Guide: GA4GH Reference Cloud Researcher UI

## Project Overview & Architecture

This repo serves as the researcher UI for the refcloud.

* **Tech Stack:** Next JS, TypeScript, Tailwind

### Directory Layout
* `app/` - Next JS app router
* `components/` - reusable React components
* `public/` - public web/art assets
* `styles/` - Global CSS styles
* `utils/` - resuable functions

---

## Core Commands

### Development & Build

* Start local dev server: `npm run dev`

### Code Quality & Validation

* TBD: Run linter
* TBD: Fix linting/formatting errors
* TBD: Run type check

### Testing

* TBD: Run all tests
* TBD: Run specific test file

## Things to Note:

* The Hydra admin API (port 4445) is called directly from server-side code and never goes through Caddy.
* Hydra redirect URIs must match the client registration in refcloud-setup.sh exactly.
* The /api/.ory proxy forwards to ORY_SDK_URL.
