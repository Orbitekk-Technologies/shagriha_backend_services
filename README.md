# Shagriha backend services

Spring Boot REST API for the Shagriha rental platform. It requires Java 21,
PostgreSQL, and the PostGIS extension. Flyway owns the database schema; do not
create the application tables manually and do not enable Hibernate schema
generation.

## Stack and requirements

- Java 21 and Maven Wrapper (`./mvnw`)
- Spring Boot 4.1
- PostgreSQL 17 with PostGIS 3.5 (the supplied Compose file uses
  `postgis/postgis:17-3.5`)
- Flyway database migrations
- A reverse proxy such as Nginx for TLS and the public API hostname

Docker is optional for the Java application, but it is the simplest way to run
the correct PostgreSQL/PostGIS version.

## Database and schema

The database is PostgreSQL **with PostGIS**, not plain PostgreSQL. Migration
`V1__initial_schema.sql` starts with `CREATE EXTENSION IF NOT EXISTS postgis`
and stores property coordinates as `geography(Point, 4326)`. A GiST spatial
index is created for those coordinates. The database user must either be able
to create the extension or an administrator must run this once before the first
application start:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Confirm it is installed with:

```sql
SELECT PostGIS_Full_Version();
```

Flyway automatically applies the versioned SQL files in
`src/main/resources/db/migration` when Spring Boot starts. Hibernate is set to
`ddl-auto: validate`, so startup fails if migrations did not run or the schema
does not match. Never edit an already-applied migration; add a new migration
such as `V3__description.sql`.

Current tables:

- Identity: `users`, `tenant_profiles`, `manager_profiles`, `refresh_tokens`
- Listings: `locations`, `properties`, `property_photos`,
  `property_amenities`, `property_highlights`, `tenant_favorites`
- Rentals: `applications`, `leases`, `payments`, `payment_methods`

The schema uses foreign keys, uniqueness/check constraints, cascading deletes
where appropriate, and indexes for spatial, manager, and price lookups. Payment
methods contain provider tokens and display metadata only; card numbers and
security codes must never be stored here.

## Local run

Requirements: Java 21+ and Docker with Compose.

```bash
docker compose up -d
./mvnw spring-boot:run
```

The API is at `http://localhost:8080/api/v1`; health is at
`http://localhost:8080/api/v1/actuator/health`.

## Production environment

Set these on the server. Do not put real secrets in the source ZIP or commit
them to Git.

| Variable | Required | Example / purpose |
| --- | --- | --- |
| `DATABASE_URL` | Yes | `jdbc:postgresql://127.0.0.1:5432/shagriha` |
| `DATABASE_USERNAME` | Yes | Dedicated application DB role |
| `DATABASE_PASSWORD` | Yes | Strong DB password |
| `FRONTEND_URL` | Yes | Exact public frontend origin, e.g. `https://sandbox.example.com` (no trailing slash) |
| `JWT_ISSUER` | Recommended | Stable issuer such as `shagriha-sandbox-api` |
| `PORT` | No | Defaults to `8080` |
| `SPRING_PROFILES_ACTIVE` | For Google login | Set to `oauth` |
| `GOOGLE_CLIENT_ID` | For Google login | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | For Google login | Google OAuth secret |

The current code generates a new in-memory RSA signing key every time the API
starts. A restart therefore invalidates all existing access tokens. This is
acceptable for a sandbox, but persistent keys or a secrets manager should be
implemented before production/high-availability deployment.

## Contabo deployment

One practical layout is PostgreSQL/PostGIS in Docker and the backend as a
systemd-managed executable JAR. Adjust usernames and paths for the server.

1. Install Java 21, Docker/Compose, and Nginx. Do not expose PostgreSQL port
   `5432` publicly; allow only local/private connections.
2. Change the database password in `compose.yml` or, preferably, maintain a
   server-only Compose/environment file. Then start and verify the database:

   ```bash
   docker compose up -d postgres
   docker compose exec postgres psql -U shagriha -d shagriha -c 'SELECT PostGIS_Full_Version();'
   ```

3. Build and test the backend:

   ```bash
   ./mvnw clean test package
   ```

4. Copy `target/shagriha-backend-services-0.0.1-SNAPSHOT.jar` to a stable
   server path such as `/opt/shagriha/backend/app.jar`.
5. Put production variables in a root-readable environment file such as
   `/etc/shagriha/backend.env` and reference it from a systemd service. Start
   the JAR with:

   ```bash
   /usr/bin/java -jar /opt/shagriha/backend/app.jar
   ```

6. Configure systemd with `Restart=on-failure`, then proxy only through Nginx.
   Route the API hostname (for example `api-sandbox.example.com`) to
   `http://127.0.0.1:8080`. Preserve `Host`, `X-Real-IP`,
   `X-Forwarded-For`, and `X-Forwarded-Proto` headers.
7. After the manager creates the DNS CNAME/A record, issue a TLS certificate
   (for example with Certbot), reload Nginx, and verify:

   ```bash
   curl --fail https://api-sandbox.example.com/api/v1/actuator/health
   ```

On first successful startup, inspect the logs for Flyway migration success.
Back up the PostgreSQL volume/database before upgrades and before applying new
migrations.

## Implemented API

- `POST /auth/signup`, `POST /auth/login`, `GET /auth/me`
- `GET|PATCH /tenants/me` and tenant favorites/residences
- `GET|POST /applications`, `PUT /applications/{id}/status`
- `GET /leases`, `GET /leases/{id}/payments`
- `GET|PATCH|PUT /managers/me` and manager properties
- `GET /properties`, `GET /properties/{id}`, `POST /properties`
- `GET /properties/{id}/leases`
- `GET /oauth2/authorization/google` with the `oauth` profile

Protected requests use `Authorization: Bearer <access-token>`.

## Google OAuth callback

Register the public backend callback with Google when OAuth is enabled:

```text
https://api-sandbox.example.com/api/v1/login/oauth2/code/google
```

## Verification and operations

```bash
./mvnw test
systemctl status shagriha-backend
journalctl -u shagriha-backend -n 200 --no-pager
```

Keep database backups outside the application server and test restoration.
