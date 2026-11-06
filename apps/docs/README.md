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

## Static Analysis

Static analysis runs in CI on every pull request that changes `apps/docs/`, and weekly so that new dependency advisories surface. All tools are pinned, so local runs match CI. Run the checks locally before opening a PR:

| Check | Command |
|-|-|
| Formatting ([Prettier](https://prettier.io/)) | `npm run format:check` |
| Linting ([ESLint](https://eslint.org/) with [`@docusaurus/eslint-plugin`](https://docusaurus.io/docs/api/misc/@docusaurus/eslint-plugin)) | `npm run lint` |
| Type checking (`tsc --noEmit`) | `npm run typecheck` |
| Dependency vulnerabilities ([audit-ci](https://github.com/IBM/audit-ci), high severity and above) | `npm run audit:deps` |
| Security rules ([Semgrep](https://semgrep.dev/), Node.js/TypeScript/React rulesets) | `npm run semgrep` |

Semgrep runs through `uvx`, so it requires [uv](https://docs.astral.sh/uv/) (also used by the admin CLI).

To fix formatting and auto-fixable lint errors: `npm run format && npm run lint:fix`

Advisories with no compatible fix are allowlisted, each with a reason, in `audit-ci.jsonc`.
