<img src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-full-color.svg" alt="GA4GH Logo" style="width: 400px;"/>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://opensource.org/licenses/Apache-2.0)

# GA4GH Reference Cloud API

Core API for the [GA4GH Reference Cloud](https://github.com/ga4gh/ga4gh-reference-cloud).

## Local development

These instructions were written against macOS. Linux works the same way apart from the package manager commands.

### Prerequisites

| Requirement | Purpose | Install (macOS) |
|---|---|---|
| Java 25+ | Spring Boot 4 runtime. The Docker image builds on Corretto 25, and Java 26 also works locally. | [SDKMAN](https://sdkman.io/) (below) |
| Gradle 9.5.1 | Optional. `./gradlew` downloads the pinned version on first run. | `sdk install gradle 9.5.1` |
| PostgreSQL 18+ | Backing store for the API, plus databases for Ory Kratos and Ory Hydra | `brew install postgresql@18` |
| Liquibase | Applies the API schema. The app runs with `ddl-auto: none` and does not create tables itself. | `brew install liquibase` |
| Docker (Desktop or OrbStack) | Runs Ory Kratos (identity/sessions), Ory Hydra (OAuth2/JWT issuer) and Mailslurper | [docker.com](https://www.docker.com/products/docker-desktop/) |

#### Installing Java with SDKMAN

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"   # or open a new terminal

sdk list java | grep amzn                   # pick a 25.x (or newer) Corretto build
sdk install java 25.0.3-amzn                # answer "Y" to make it the default
```

Verify the build is going to use the right JDK. `./gradlew` uses `JAVA_HOME`:

```bash
java -version
echo $JAVA_HOME     # ~/.sdkman/candidates/java/current
```

If `java -version` reports an older JDK, check `~/.zshrc` and `~/.zprofile` for an existing `JAVA_HOME` or Homebrew `openjdk` `PATH` entry that is taking precedence. Remove it, or make sure the SDKMAN init block comes last.

### 1. Start PostgreSQL and create the databases

```bash
brew services start postgresql@18

psql postgres <<'SQL'
CREATE ROLE refcloudapi LOGIN PASSWORD 'secret';
CREATE ROLE kratos      LOGIN PASSWORD 'secret';
CREATE ROLE hydra       LOGIN PASSWORD 'secret';
CREATE DATABASE refcloudapi OWNER refcloudapi;
CREATE DATABASE kratos      OWNER kratos;
CREATE DATABASE hydra       OWNER hydra;
SQL
```

These credentials match `src/main/resources/application.yml` and `docker-compose.yml`. They are for local development only.

### 2. Apply the API schema

```bash
cd liquibase
liquibase --changeLogFile=dbchangelog.xml \
  --url=jdbc:postgresql://localhost:5432/refcloudapi \
  --username=refcloudapi --password=secret \
  update
cd ..
```

If you don't want to install Liquibase locally, `liquibase/Dockerfile` packages the same changelog.

Optional test data:

```bash
psql -U refcloudapi -d refcloudapi -f src/test/resources/sql/add-test-data.sql
psql -U refcloudapi -d refcloudapi -f src/test/resources/sql/add-test-passport-visa-assertions.sql
```

### 3. Start the Ory services

```bash
docker compose up -d
```

This runs the Kratos and Hydra migrations against the host Postgres (via `host.docker.internal`), then starts:

| Service | Ports |
|---|---|
| Kratos | 4433 (public), 4434 (admin) |
| Hydra | 4444 (public / JWT issuer), 4445 (admin), 5555 |
| Mailslurper | 4436, 4437 |

Verification and recovery emails sent by Kratos land in Mailslurper at `http://127.0.0.1:4436`.

#### Optional: point Kratos self-service URLs at the UI

`contrib/kratos/kratos.yml` sends its self-service redirects (verification and recovery email links, post-logout redirects, error pages) to `127.0.0.1:4455`, the default port of Ory's sample UI. The [Reference Cloud UI](https://github.com/ga4gh/refcloud-ui) runs on `127.0.0.1:3000` and serves the same paths. Login and registration work without this change because the UI creates those flows through its own Ory proxy, so you can skip it for normal local development. If you need those redirects and email links to land in the UI, run:

```bash
sed -i '' 's#127.0.0.1:4455#127.0.0.1:3000#g' contrib/kratos/kratos.yml   # macOS; on Linux use sed -i without ''
docker compose restart kratos
```

### 4. Run the API

```bash
./gradlew bootRun
```

The API listens on `http://localhost:8080`.

### 5. Verify

```bash
curl http://localhost:8080/ga4gh/drs/v1/service-info
```

You should get back the DRS `service-info` document configured under `ga4gh.refcloud.drs.service-info` in `application.yml`.

Other public endpoints:

- `GET /ga4gh/drs/v1/objects/{id}`
- `GET /datasets`

Endpoints that need a GA4GH Passport require Hydra to be running so the API can validate JWTs against the issuer at `http://127.0.0.1:4444`. A full browser login flow also needs the [Reference Cloud UI](https://github.com/ga4gh/refcloud-ui); see its README for setup, including registering the Hydra OAuth client.

### Troubleshooting

- **`exec format error` on Apple Silicon.** The pinned `oryd/kratos:v0.9.0-alpha.2` and `oryd/mailslurper` images may not have arm64 builds. Add `platform: linux/amd64` to those services in `docker-compose.yml`.
- **The `kratos-migrate` or `hydra-migrate` container can't reach Postgres.** Confirm Postgres accepts connections from Docker. You may need `listen_addresses = '*'` in `postgresql.conf` and a `pg_hba.conf` entry for the Docker network.
- **Kratos rejects its config.** `contrib/kratos/kratos.yml` declares `version: v0.7.1-alpha.1` while the compose file pins Kratos v0.9.0-alpha.2. Check that mismatch first.
- **The API fails at startup with relation/table errors.** The Liquibase step (2) hasn't been run against `refcloudapi`.

## Configuration

Configuration lives in `src/main/resources/application.yml`. Spring Boot's relaxed binding lets you override any property with an environment variable. The ones you are most likely to change:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/refcloudapi` | API database JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `refcloudapi` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | Database password |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI` | `http://127.0.0.1:4444` | OAuth2/OIDC issuer used to validate JWTs (Hydra) |
| `GA4GH_REFCLOUD_SECURITY_KRATOS_PUBLICBASEURL` | `http://127.0.0.1:4433` | Kratos public API, used for session validation |
| `GA4GH_REFCLOUD_DRS_SCHEME` | `http` | Scheme used when building DRS URIs |
| `GA4GH_REFCLOUD_DRS_HOSTDOMAIN` | `127.0.0.1:8080` | Host used when building DRS URIs |

## Issues

For any issues relating to the API, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.
