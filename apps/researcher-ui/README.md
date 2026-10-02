# GA4GH Reference Cloud UI

UI for the [GA4GH Reference Cloud](https://github.com/ga4gh/ga4gh-reference-cloud)

## Local development

These instructions were written against macOS. Linux works the same way apart from the package manager commands.

### Prerequisites

| Requirement | Purpose | Install (macOS) |
|---|---|---|
| Node.js 24 | Next.js dev server. The Docker image pins 24.15.0. | `brew install node@24`, or `nvm install 24.15.0` |
| A running [`refcloud-api`](https://github.com/ga4gh/refcloud-api) stack | Backend API on `:8080`, plus Postgres, Ory Kratos and Ory Hydra | Follow the `refcloud-api` README through its "Verify" step |

If you use Homebrew's `node@24`, follow the caveat it prints to put it on your `PATH`, then confirm with `node -v`.

### 1. Start the API stack

In the `refcloud-api` directory, with Postgres running and the schema applied (see that README):

```bash
docker compose up -d     # Ory Kratos, Ory Hydra, Mailslurper
./gradlew bootRun        # API on :8080
```

Seed the API with test data so the Datasets and DRS pages have something to show:

```bash
psql -U refcloudapi -d refcloudapi -f src/test/resources/sql/add-test-data.sql
```

### 2. Install dependencies

In this directory:

```bash
npm ci
```

`npm ci` installs exactly what `package-lock.json` specifies. Expect a long list of deprecation warnings; they are harmless.

### 3. Register the Hydra OAuth client

The Passport page runs an OAuth2 authorization-code flow against Hydra, using a client that must already be registered. Nothing creates it automatically, so register it once against Hydra's admin API:

```bash
curl -s -X POST http://127.0.0.1:4445/admin/clients \
  -H 'Content-Type: application/json' \
  -d '{
    "client_id": "ga4gh-reference-cloud-researcher-client",
    "client_secret": "secret",
    "grant_types": ["authorization_code", "refresh_token"],
    "response_types": ["code"],
    "scope": "openid offline_access profile",
    "redirect_uris": ["http://127.0.0.1:3000/passport", "http://127.0.0.1:3000/drs"],
    "token_endpoint_auth_method": "client_secret_post"
  }'
```

Notes:

- `token_endpoint_auth_method` must be `client_secret_post`. `pages/api/oauth/token-exchange.tsx` sends the client secret in the request body, and Hydra's default (`client_secret_basic`) rejects that.
- `client_id` and `client_secret` must match `PUB_HYDRA_RESEARCHER_CLIENT_ID` and `HYDRA_RESEARCHER_CLIENT_SECRET` below.
- The client is stored in Hydra's Postgres database, so you only need to do this again if you drop that database. To check whether it exists: `curl -s http://127.0.0.1:4445/admin/clients/ga4gh-reference-cloud-researcher-client`.

### 4. Configure environment variables

Create a `.env.local` file in this directory. Next.js loads it automatically, and `.gitignore` already excludes it:

```bash
cat > .env.local <<'EOF'
PUB_UI_BASE_URL=http://127.0.0.1:3000
PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL=http://127.0.0.1:4444
PUB_HYDRA_RESEARCHER_CLIENT_ID=ga4gh-reference-cloud-researcher-client
PUB_REFCLOUD_DOCS_URL=https://docs.refcloud.ga4gh.org
ORY_SDK_URL=http://127.0.0.1:4433/
KRATOS_PUBLIC_API_BASE_URL=http://127.0.0.1:4433
HYDRA_PUBLIC_API_SERVER_SIDE_BASE_URL=http://127.0.0.1:4444
HYDRA_ADMIN_API_BASE_URL=http://127.0.0.1:4445
HYDRA_RESEARCHER_CLIENT_SECRET=secret
REFCLOUD_API_BASE_URL=http://127.0.0.1:8080
EOF
```

Exporting the same variables in your shell also works. Variables beginning with `PUB_` are served to the browser through `/api/config/env`, so they must not contain secrets. See [Configuration](#configuration) for what each one does.

### 5. Run the UI

```bash
npm run dev
```

Open **`http://127.0.0.1:3000`** in your browser. Use `127.0.0.1`, not `localhost`: the API's CORS allowlist, the Hydra client's redirect URI and the Kratos session cookies are all tied to `127.0.0.1`, and mixing hostnames causes logins to silently fail.

### 6. Verify

Walk through the app in this order:

1. **Register** an account. Kratos calls the API's registration webhook (`:8080/webhook/kratos/registration`), which creates the matching passport user.
2. **Log in.**
3. Open **Datasets** and **DRS**. These should show the seeded test data.
4. Open **Passport**. This runs the Hydra authorization-code flow and displays your passport token.

Log in before opening the Passport page. See [Known issues](#known-issues).

### Optional: point Kratos self-service URLs at the UI

The Kratos config used locally (`refcloud-api/contrib/kratos/kratos.yml`) sends verification and recovery email links, post-logout redirects and error pages to `127.0.0.1:4455`, the default port of Ory's sample UI. Login and registration work without changing this, because this UI creates those flows through its own Ory proxy (`/api/.ory`). If you need those redirects and email links to land in this UI, see the optional Kratos step in the `refcloud-api` README.

Kratos emails go to Mailslurper at `http://127.0.0.1:4436`.

### Known issues

- **Opening the Passport page while logged out hangs.** In `pages/api/oauth/login.tsx`, the logged-out branch returns a `NextResponse`, an App Router API that this Pages Router handler never sends, and its redirect target (`HYDRA_ADMIN_API_BASE_URL/login`) does not exist. Log in first and this code path is skipped.
- **`contrib/kratos/` in this repo is not used.** Local development mounts the Kratos config from `refcloud-api/contrib/kratos/`. The copy here targets an older passport-broker webhook (`:4501`) and is out of date.

### Troubleshooting

- **Passport page returns "Hydra rejected code swap".** The OAuth client is missing or was registered with the wrong auth method. Re-run step 3 and check `token_endpoint_auth_method` is `client_secret_post`.
- **Logged in, but the next page acts logged out.** You're probably mixing `localhost` and `127.0.0.1`. Use `127.0.0.1` everywhere, in both the browser and `.env.local`.
- **Datasets or DRS pages are empty.** The API has no data. Run the seed script in step 1.

## Configuration

| Variable Name | Description | Example |
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

## Issues

For any issues relating to the UI, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.
