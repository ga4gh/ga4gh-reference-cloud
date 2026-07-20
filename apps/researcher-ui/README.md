# GA4GH Reference Cloud UI

UI for the [GA4GH Reference Cloud](https://github.com/ga4gh/ga4gh-reference-cloud)

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine)
  * Docker
  * Node.js (developed using v24.15.0)
* Clone the [`refcloud-api`](https://github.com/ga4gh/refcloud-api) repo. In the `refcloud-api` directory:
  * run `docker-compose up -d` to spin up dependency services (Ory Kratos and Hydra)
  * run `./gradlew bootRun` to start the `refcloud-api` server
* Install dependencies: `npm install`
* Set the following environment variables (NOTE: variables beginning with `PUB_` are public/client facing and should not contain sensitive data):
  ```
  export PUB_UI_BASE_URL="http://127.0.0.1:3000"
  export PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL="http://127.0.0.1:4444"
  export PUB_HYDRA_RESEARCHER_CLIENT_ID="ga4gh-reference-cloud-researcher-client"
  export PUB_REFCLOUD_DOCS_URL="https://docs.refcloud.ga4gh.org"
  export ORY_SDK_URL="http://localhost:4433/"
  export KRATOS_PUBLIC_API_BASE_URL="http://127.0.0.1:4433"
  export HYDRA_PUBLIC_API_SERVER_SIDE_BASE_URL="http://127.0.0.1:4444"
  export HYDRA_ADMIN_API_BASE_URL="http://127.0.0.1:4445"
  export HYDRA_RESEARCHER_CLIENT_SECRET="secret"
  export REFCLOUD_API_BASE_URL="http://127.0.0.1:8080"
  ```
* Run the UI server in dev mode: `npm run dev`
* Access the UI server via browser at `http://127.0.0.1:3000`

## Configuration

Configure the UI app via the following environment variables

| Variable Name | Description | Example |
|---------------|-------------|---------|
| `PUB_UI_BASE_URL` | base URL to UI server (this app) | `http://127.0.0.1:3000` |
| `PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL` | base URL to Ory Hydra Public API from browser/client side | `http://127.0.0.1:4444` |
| `PUB_HYDRA_RESEARCHER_CLIENT_ID` | client ID registered in Ory Hydra | `ga4gh-reference-cloud-researcher-client` |
| `PUB_REFCLOUD_DOCS_URL` | link to reference cloud documentation site (linked to from multiple places in the UI) | `https://docs.refcloud.ga4gh.org` |
| `ORY_SDK_URL` | base URL to the Ory Kratos Public API | `http://127.0.0.1:4433/` |
| `KRATOS_PUBLIC_API_BASE_URL` | base URL to the Ory Kratos Public API | `http://127.0.0.1:4433` |
| `HYDRA_PUBLIC_API_SERVER_SIDE_BASE_URL` | base URL to Ory Hydra Public API from server side | `http://127.0.0.1:4444` |
| `HYDRA_ADMIN_API_BASE_URL` | base URL to the Ory Hydra Admin API | `http://127.0.0.1:4445` |
| `HYDRA_RESEARCHER_CLIENT_SECRET` | client secret registered in Ory Hydra | `secret` |
| `REFCLOUD_API_BASE_URL` | base URL to the Reference Cloud backend API | `http://127.0.0.1:8080` |

## Issues

For any issues relating to the UI, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.
