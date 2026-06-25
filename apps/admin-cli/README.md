# Refcloud Data Onboarding
Data onboarding CLI for the GA4GH Reference Cloud

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine)
  * Python v3.14+ (currently developed using v3.14.3)
  * uv
* to install package locally (in current directory)
  * run `uv sync`
  * run application with `uv run refcloud`
* to install package globally on your system
  * run `uv tool install .`
  * run `uv tool update-shell`
  * run application with `refcloud`