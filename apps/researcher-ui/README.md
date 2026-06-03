# GA4GH Reference Cloud UI

UI for the [GA4GH Reference Cloud](https://github.com/ga4gh/ga4gh-reference-cloud)

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine)
  * Docker
  * Node.js (developed using v24.15.0)
* Run services used by the UI: `docker-compose up -d`
* Run the dev server: `npm run dev`
* Access the dev server via browser at `http://localhost:3000`

## Configuration

Configure the UI app via the following environment variables

| Variable Name | Description |
|---------------|-------------|
| `NEXT_PUBLIC_REFCLOUD_DOCS_URL` | link to reference cloud documentation site (linked to from multiple places in the UI) |

## Issues

For any issues relating to the UI, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.
