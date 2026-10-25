# dashboard-service

The authenticated half of Shortly: owns the links and their Postgres schema (Flyway), performs
every write, publishes a read model of the links, and turns the click stream into statistics.
One process does all of it: the REST API and the click counter run in every pod.

## Stack

Java 25 (Temurin), Spring Boot 4.0.8, Gradle 9.6.1 (wrapper, Java toolchain pinned to 25 with
the Foojay resolver), jOOQ 3.20.20 open-source edition with code generation from the Flyway
migrations (DDLDatabase, no database needed to compile), Spring Security OAuth2 resource server
(Keycloak), Spring Kafka, Micrometer/Prometheus, springdoc-openapi 3.1.1,
Testcontainers.

## Running

One process: every pod serves the REST API, runs the click counter (a Kafka consumer) and the
daily deletion of old stats. Flyway migrates the schema when a pod starts; pods starting together
take turns on Flyway's lock. Flyway logs in as the owner (`DATABASE_MIGRATION_USER/PASSWORD`,
default `dashboard_owner`), the application pool as `dashboard_app` (`DATABASE_USER/PASSWORD`).
The deployed environment and local compose only change the values below. The image logs JSON
(`LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`).

## Database roles (infrastructure)

The migrations create tables and grant on them; they never create or alter roles. Each
environment's database needs, before the first deploy:

| Role | Requirement |
|---|---|
| migration user (`DATABASE_MIGRATION_USER`) | owns the database (and so schema `public`); no superuser or `CREATEROLE` needed |
| `dashboard_app` | login |
| `link_reader` | not a login: readers of the published read model log in with roles of their own, made members of it |

Timeouts are not role settings: the application sets `transaction_timeout = 2s` on its own connections.
`local/database-roles.sql` does the same setup for local compose and the integration tests, which
therefore migrate as a plain owner.

## Environment

Every setting is an environment variable with a local default.

| Variable | Default | |
|---|---|---|
| `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME` | `localhost`, `5432`, `shortly` | JDBC URL is `jdbc:postgresql://HOST:PORT/NAME` + `DATABASE_CONN_PARAMS` (default empty; production sets `?sslmode=verify-full&sslrootcert=/etc/ssl/rds/global-bundle.pem`, a bundle the image carries) |
| `DATABASE_USER`, `DATABASE_PASSWORD` | `dashboard_app` | application pool |
| `DATABASE_MIGRATION_USER`, `DATABASE_MIGRATION_PASSWORD` | `dashboard_owner` | Flyway (schema owner) |
| `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM` | `http://localhost:8083`, `shortly` | the expected token issuer is `SERVER_URL/realms/REALM` |
| `KEYCLOAK_INTERNAL_URL` | `KEYCLOAK_SERVER_URL` | where the signing keys are fetched, if different from what browsers see (Docker network locally) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG` | `PLAINTEXT`, `PLAIN`, empty | applied to the admin client and the click consumer; the SASL pair only when the protocol contains `SASL` |
| `SERVER_PORT` | `8081` in the image (Boot's `8080` when run from Gradle) | the API port; actuator listens on `8082` |
| `SHORTLY_SHORT_BASE_URL` | `http://localhost:8080/` | |
| `SPRING_KAFKA_CONSUMER_MAX_POLL_RECORDS` | `500` | click counter: clicks counted per batch |

## Build and test

```bash
./gradlew test           # unit tests, no Docker
./gradlew integrationTest   # integration tests (Docker/Testcontainers)
./gradlew check          # both suites plus SpotBugs
./gradlew build          # check + assemble: generates jOOQ classes, writes the JaCoCo report
./gradlew spotlessApply  # google-java-format (AOSP, 4 spaces); `spotlessCheck` runs in CI
docker build -t shortly/dashboard-service:dev .
```

The image builds the jar itself (no local JDK needed), runs as a non-root user, and bundles a
pinned `dd-java-agent` (verified by SHA-256) started through `JAVA_OPTS`/`ENTRYPOINT`. Pass
`--build-arg DD_GIT_REPOSITORY_URL=… --build-arg DD_GIT_COMMIT_SHA=…` to link traces to the source.

Local test roles use their names as passwords (`dashboard_owner`, `dashboard_app`).

## Contracts

- `contract/link-contract.sql`: the links read model this service publishes (the `link_snapshot`
  view, including the change feed's `change_seq`), readable by members of `link_reader`.
  `LinkContractTest` proves the migrated schema matches the file.
- The click events this service consumes from the `clicks` topic: the JSON of `model/ClickEvent`,
  whose annotations are the rules a click must meet to be counted.

Each repository that uses a contract holds a verbatim copy.

## Layout

One package per layer under `io.shortly.dashboard`. Three entry points: `controller` (HTTP
mapping and the problem responses), `consumer` (the click counter, from Kafka) and `scheduler`
(the daily deletion of old stats). Then `dto` (request and response bodies) · `service` (links,
stats) · `repository` (all SQL) · `model` · `exception` · `util` (short codes, target URL
checks) · `config` (security, OpenAPI, Kafka).
Production values for the shared `shortly-service` chart (in `infra`) in `charts/values.production.yaml`, CI
workflows under `.github/workflows/`.

## Deploy

Both workflows call `infra`'s shared ones, pinned to an `infra` release (`@v1.0.0`).

Publishing a GitHub Release (tag `vX.Y.Z`) deploys to the one environment, production, through
`deploy.yaml` → `infra`'s `deploy-service.yml`: validate the tag → build and push to ECR → Trivy →
SBOM + cosign → `helm upgrade --install --atomic --wait --timeout 10m` of `infra`'s published
`shortly-service` chart (`chart-version`) with `charts/values.production.yaml` → this service's
`.github/smoke-test.sh`. Local runs use `infra/local/docker-compose.yml`.

`code_quality.yaml` → `infra`'s `code-quality.yml` runs on every PR, on main and nightly:
gitleaks, `spotlessCheck build` (tests + JaCoCo). AWS access is OIDC
(`role-to-assume`), no static keys.
Repository variables: `AWS_REGION`, `ECR_REGISTRY`, `AWS_ECR_PUSH_ROLE_ARN`. GitHub environment `production`: `AWS_DEPLOY_ROLE_ARN`, `EKS_CLUSTER_NAME`, and
required reviewers.

## Operate

- **Secrets:** `externalSecrets` in the chart renders one `ExternalSecret` per entry (the
  application's database login, Flyway's owner login, and Kafka). Remote keys are
  `shortly/production/dashboard-service/<name>` in AWS Secrets Manager (JSON with `username`, `password`,
  `bootstrap_servers`, `jaas_config`). Reloader restarts the Deployment when a Secret changes.
- **Swagger UI login:** the realm's `shortly-dashboard` client must list
  `https://dashboard.shortly.internal/swagger-ui/oauth2-redirect.html` as a redirect URI and its
  origin as a web origin.
- **What AWS and the cluster provide** (database roles, Kafka, Keycloak, the load balancer
  classes, secrets): `infra`'s `AWS.md`.
- **Datadog:** the Deployment carries `tags.datadoghq.com/{env,service,version}` labels (version =
  the release tag, set by the pipeline) and `DD_ENV/DD_SERVICE/DD_VERSION/DD_AGENT_HOST`. Metrics come from an autodiscovery OpenMetrics check
  on `/actuator/prometheus` (namespace `shortly`: `shortly_*`, `http_server_requests*`, `jvm_*`,
  `hikaricp_*`, `kafka_*`). Needs a Datadog agent DaemonSet on the nodes.
- **Rollout:** `maxSurge 25%`, `maxUnavailable 0`, a 5-minute startup probe; the first new pod migrates the schema (migrations
  only add, so old pods keep working); `--atomic` rolls back a failed rollout, a failing migration
  included.
- Check the values without a cluster:
  `helm template dashboard-service ../infra/charts/shortly-service --values charts/values.production.yaml --set image.repository=x --set image.digest=sha256:0`.

## Behaviour

- Target URLs keep their percent-encoding exactly as given (`?q=c%2B%2B` stays encoded); non-ASCII path characters are percent-encoded so `Location` headers survive.
- IP-address hosts are refused in every spelling browsers accept (`2130706433`, `0177.0.0.1`, `127.1`, `[fd00::1]`), public ones too: a target needs a host name.
- The click counter counts each batch in one transaction, then commits its Kafka offsets: no click is lost. A database failure is retried without limit (back-off growing from 1 s to 30 s), and a batch whose pod stops before counting it is read again by the next pod. A pod killed between the database commit and the offset commit counts that one batch twice (accepted). Only records that are not usable clicks are skipped (counter `shortly.stats.discarded`).
- A record on `clicks` that is not a click or breaks the click rules (code, country, referrer host, time) is skipped and counted instead of stopping the partition. A rejected Kafka login is retried every 10 s instead of stopping the consumer; a consumer that stops or falls behind shows up as lag of the `shortly-stats` group (Datadog Data Streams Monitoring; the alert itself is Datadog configuration).
- `lastClickAt` is the newest click's own time, not the day it falls in.
