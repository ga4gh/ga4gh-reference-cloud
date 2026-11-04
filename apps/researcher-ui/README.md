# GA4GH Reference Cloud: Researcher UI

Researcher UI for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the researcher UI by itself, see below.

**Note:** you MUST install all [required prerequisites](../../README.md#getting-started) AND run the [setup script](../../scripts/refcloud-setup.sh) first to create required resources.

## Run

* Install dependencies: `npm install`
* Run local dev server: `npm run dev`

## Configuration

| Environment Variable Name | Description | Example |
|---------------|-------------|---------|
| `PUB_UI_BASE_URL` | Base URL of this UI server. Also used to build the OAuth redirect URI (`<base>/passport`). | `http://127.0.0.1:3000` |
| `PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL` | Ory Hydra Public API as reached from the browser | `http://127.0.0.1:4444` |
| `PUB_HYDRA_RESEARCHER_CLIENT_ID` | OAuth client ID registered in Ory Hydra (see step 3) | `ga4gh-reference-cloud-researcher-client` |
| `PUB_REFCLOUD_DOCS_URL` | Reference Cloud documentation site, linked from several places in the UI | `https://docs.refcloud.ga4gh.org` |
| `ORY_SDK_URL` | Ory Kratos Public API, used by the `/api/.ory` proxy | `http://127.0.0.1:4433/` |
| `KRATOS_PUBLIC_API_BASE_URL` | Ory Kratos Public API, used server-side to check sessions | `http://127.0.0.1:4433` |
| `HYDRA_PUBLIC_API_SERVER_SIDE_BASE_URL` | Ory Hydra Public API as reached from the UI server (token exchange) | `http://127.0.0.1:4444` |
| `HYDRA_ADMIN_API_BASE_URL` | Ory Hydra Admin API (login and consent acceptance) | `http://127.0.0.1:4445` |
| `HYDRA_RESEARCHER_CLIENT_SECRET` | OAuth client secret registered in Ory Hydra (see step 3) | `secret` |
| `REFCLOUD_API_BASE_URL` | Reference Cloud backend API | `http://127.0.0.1:8080` |
