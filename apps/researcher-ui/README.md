# GA4GH Reference Cloud UI

UI for the [GA4GH Reference Cloud](https://github.com/ga4gh/ga4gh-reference-cloud)

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine)
  * Docker
  * Node.js (developed using v24.15.0)
* Install dependencies: `npm install`
* Set the following environment variables:
  ```
  export NEXT_PUBLIC_BASE_URL="http://127.0.0.1:3000"
  export ORY_SDK_URL="http://localhost:4433/"
  export KRATOS_PUBLIC_API_BASE_URL="http://127.0.0.1:4433"
  export NEXT_PUBLIC_HYDRA_PUBLIC_API_BASE_URL="http://127.0.0.1:4444"
  export HYDRA_ADMIN_API_BASE_URL="http://127.0.0.1:4445"
  export NEXT_PUBLIC_HYDRA_RESEARCHER_CLIENT_ID="ga4gh-reference-cloud-researcher-client"
  export HYDRA_RESEARCHER_CLIENT_SECRET="<client-secret>"
  ```
* Run services used by the UI: `docker-compose up -d`
* Run the dev server: `npm run dev`
* Access the dev server via browser at `http://localhost:3000`

## Configuration

Configure the UI app via the following environment variables

| Variable Name | Description | Example |
|---------------|-------------|---------|
| `NEXT_PUBLIC_BASE_URL` | base URL to UI server (this app) | `http://127.0.0.1:3000` |
| `ORY_SDK_URL` | base URL to the Ory Kratos Public API | `http://127.0.0.1:4433/` |
| `KRATOS_PUBLIC_API_BASE_URL` | base URL to the Ory Kratos Public API | `http://127.0.0.1:4433` |
| `NEXT_PUBLIC_HYDRA_PUBLIC_API_BASE_URL` | base URL to Ory Hydra Public API | `http://127.0.0.1:4444` |
| `HYDRA_ADMIN_API_BASE_URL` | base URL to the Ory Hydra Admin API | `http://127.0.0.1:4445` |
| `NEXT_PUBLIC_HYDRA_RESEARCHER_CLIENT_ID` | client ID registered in Ory Hydra | `ga4gh-reference-cloud-researcher-client` |
| `HYDRA_RESEARCHER_CLIENT_SECRET` | client secret registered in Ory Hydra | `secure-string` |
| `NEXT_PUBLIC_REFCLOUD_DOCS_URL` | link to reference cloud documentation site (linked to from multiple places in the UI) | `https://docs.refcloud.ga4gh.org` |

## Issues

For any issues relating to the UI, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.
