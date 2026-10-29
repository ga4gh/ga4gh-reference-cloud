# GA4GH Reference Cloud: Docs

Documentation site for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the docs by itself, see below.

**Note:** you MUST install all [required prerequisites](../../README.md#getting-started) AND run the [setup script](../../scripts/refcloud-setup.sh) first to create required resources.

## Run

The docs site is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

* Install dependencies: `npm install`
* Run local dev server: `npm run start`
* Build "production" server: `npm run build`
* Rebuild GA4GH OpenAPI Documentation: `npm run clear && npx docusaurus gen-api-docs all`
