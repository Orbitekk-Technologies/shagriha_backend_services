# Shagriha Deployment Runbook

Last updated: September 1, 2026

This document records the verified sandbox deployment process for Shagriha and provides a safe basis for future test and production releases. It intentionally contains no passwords, OAuth secrets, or API tokens.

## 1. Current architecture

| Component | Public address | Server/process |
| --- | --- | --- |
| Sandbox frontend | `https://sandbox.shagriha.com` | Apache → Next.js on `127.0.0.1:3000`, managed by PM2 |
| Sandbox API | `https://api.shagriha.com` | Apache → Spring Boot on `127.0.0.1:8080`, managed by systemd |
| Sandbox database | Not public-facing | PostgreSQL 16 + PostGIS 3.4, database `shagriha_db` |
| Existing live site | `https://shagriha.com` / `https://www.shagriha.com` | Old server `178.18.242.24`; not changed during this deployment |
| New VPS | Sandbox and API | `212.28.189.194` |

Application directories on the new VPS:

```text
/root/shagriha/frontend
/root/shagriha/backend
```

Stable backend artifact:

```text
/opt/shagriha/backend/app.jar
```

## 2. Release deployed on September 1, 2026

The following commits were deployed:

```text
Frontend main:                              6b67f69
Backend feature/springboot-api-foundation: fbc0e65
```

The backend startup applied Flyway migrations:

```text
V6 - single user role
V7 - property preferences and fees
```

Flyway reported successful migration from schema version 5 to version 7. Existing sandbox records were preserved. At verification time the database contained 15 users and 2 properties.

No local development database was copied. Consequently, local dummy properties were not deployed.

## 3. Changes made during deployment

### Backend configuration

Production settings previously embedded in tracked YAML files were moved to:

```text
/root/shagriha/backend/.env.local
```

This file is ignored by Git and restricted to root:

```bash
chmod 600 /root/shagriha/backend/.env.local
```

It defines these keys:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
FRONTEND_URL
PORT
SPRING_PROFILES_ACTIVE
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GOOGLE_REDIRECT_URI
SESSION_COOKIE_SECURE
JWT_ISSUER
JWT_ACCESS_TOKEN_TTL
JWT_REFRESH_TOKEN_TTL
MAPBOX_ACCESS_TOKEN
MAPBOX_NEARBY_CACHE_TTL
```

Important non-secret values for sandbox:

```properties
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/shagriha_db
DATABASE_USERNAME=shagriha_db
FRONTEND_URL=https://sandbox.shagriha.com
PORT=8080
SPRING_PROFILES_ACTIVE=oauth
GOOGLE_REDIRECT_URI=https://api.shagriha.com/api/v1/login/oauth2/code/google
SESSION_COOKIE_SECURE=true
JWT_ISSUER=shagriha-sandbox-api
JWT_ACCESS_TOKEN_TTL=PT1H
JWT_REFRESH_TOKEN_TTL=P30D
MAPBOX_NEARBY_CACHE_TTL=P7D
```

### Frontend configuration

The frontend build uses:

```text
/root/shagriha/frontend/.env
```

Required values:

```properties
NEXT_PUBLIC_API_BASE_URL=https://api.shagriha.com/api/v1/
NEXT_PUBLIC_DEMO_MODE=false
NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN=server-specific-value
```

`NEXT_PUBLIC_*` values are embedded into the browser bundle during `npm run build`. Changing `.env` without rebuilding does not update the deployed browser application.

### Backend process management

The old `nohup mvn spring-boot:run` process was replaced with a systemd service:

```text
/etc/systemd/system/shagriha-backend.service
```

Service definition:

```ini
[Unit]
Description=Shagriha Spring Boot Backend
After=network-online.target postgresql.service
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/shagriha/backend
ExecStart=/usr/bin/java -jar /opt/shagriha/backend/app.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

The service was enabled for reboot persistence.

### Frontend process management

The frontend continues to run under PM2 as `shagriha-frontend`. Its process list was saved, and `pm2-root` is enabled and active for reboot persistence.

## 4. Backups created

Deployment backups were stored at:

```text
/root/deployment-backup-2026-09-01/
```

The directory contains the pre-deployment database dump, old backend configuration, frontend environment, and package manifests. The PostgreSQL custom-format dump was validated with `pg_restore --list` before deployment.

Never commit this directory or copy it to an insecure location because it contains credentials and application data.

## 5. Routine sandbox deployment

### 5.1 Pre-deployment checks

Connect to the server:

```bash
ssh root@212.28.189.194
```

Check current services and disk space:

```bash
df -h /
systemctl status shagriha-backend --no-pager
pm2 list
curl --fail http://127.0.0.1:8080/api/v1/actuator/health
curl --fail -I http://127.0.0.1:3000
```

Check Git status before pulling:

```bash
git -C /root/shagriha/backend status --short --branch
git -C /root/shagriha/frontend status --short --branch
```

Stop if tracked files have unexpected modifications. Do not use `git reset --hard` to bypass this check.

Verify secret files remain ignored and protected:

```bash
git -C /root/shagriha/backend check-ignore -v .env.local
git -C /root/shagriha/frontend check-ignore -v .env
stat -c '%a %U:%G %n' /root/shagriha/backend/.env.local /root/shagriha/frontend/.env
```

### 5.2 Back up the database

The `postgres` OS user cannot write under `/root`, so create the dump in its own directory and then move it:

```bash
sudo -u postgres pg_dump -Fc shagriha_db \
  -f /var/lib/postgresql/shagriha_db-before-release.dump

mv /var/lib/postgresql/shagriha_db-before-release.dump \
  /root/shagriha_db-before-release.dump

chmod 600 /root/shagriha_db-before-release.dump
pg_restore --list /root/shagriha_db-before-release.dump | head -20
```

Use a dated filename for every real release and move long-term backups to an appropriately secured backup system.

### 5.3 Fetch and inspect

```bash
git -C /root/shagriha/backend fetch --prune origin
git -C /root/shagriha/frontend fetch --prune origin
```

Inspect commits before deployment:

```bash
git -C /root/shagriha/backend --no-pager log --oneline HEAD..origin/feature/springboot-api-foundation
git -C /root/shagriha/frontend --no-pager log --oneline HEAD..origin/main
```

Review any new Flyway files under `src/main/resources/db/migration`. Never edit a migration already applied to a database.

### 5.4 Update and build the backend

```bash
git -C /root/shagriha/backend pull --ff-only origin feature/springboot-api-foundation

cd /root/shagriha/backend
./mvnw clean package -DskipTests
```

Tests should be run in CI or an isolated test environment. `-DskipTests` is used on this VPS to prevent tests from accidentally touching the configured sandbox database.

Only continue after `BUILD SUCCESS`:

```bash
cp target/shagriha-backend-services-0.0.1-SNAPSHOT.jar \
  /opt/shagriha/backend/app.jar

systemctl restart shagriha-backend
systemctl status shagriha-backend --no-pager
journalctl -u shagriha-backend -n 120 --no-pager
curl --fail http://127.0.0.1:8080/api/v1/actuator/health
```

Confirm Flyway validation/migration and application startup in the journal before deploying the frontend.

### 5.5 Update and build the frontend

```bash
git -C /root/shagriha/frontend pull --ff-only origin main

cd /root/shagriha/frontend
npm ci
npm run build
```

Do not restart PM2 if the build fails. If it succeeds:

```bash
pm2 restart shagriha-frontend --update-env
pm2 save
pm2 list
pm2 logs shagriha-frontend --lines 60 --nostream
```

Do not run `npm audit fix` directly on the server. Dependency fixes should be reviewed, tested, committed, and deployed through Git.

## 6. Post-deployment verification

### Health and routes

```bash
curl --fail -I https://sandbox.shagriha.com
curl --fail -I https://sandbox.shagriha.com/search
curl --fail -I https://sandbox.shagriha.com/managers/newproperty
curl --fail https://api.shagriha.com/api/v1/actuator/health
```

### CORS

```bash
curl --silent --show-error --dump-header - --output /dev/null \
  --request OPTIONS \
  https://api.shagriha.com/api/v1/auth/me \
  --header 'Origin: https://sandbox.shagriha.com' \
  --header 'Access-Control-Request-Method: GET' \
  --header 'Access-Control-Request-Headers: Authorization'
```

Expected header:

```text
Access-Control-Allow-Origin: https://sandbox.shagriha.com
```

### OAuth

```bash
curl --silent --show-error --head \
  https://api.shagriha.com/api/v1/oauth2/authorization/google | head -1
```

Expected status is `302`. The authorization response must contain:

```text
redirect_uri=https://api.shagriha.com/api/v1/login/oauth2/code/google
```

Google Cloud must register that exact authorized redirect URI and the applicable frontend origin. Test the complete login in a private browser window after every authentication-related release.

### Manual smoke tests

1. Open the sandbox in a private browser window.
2. Test password login and Google login.
3. Confirm the OAuth flow returns to `/oauth/callback` on the sandbox frontend.
4. Search for properties and open property details.
5. Test manager onboarding and the property form.
6. Confirm expected users and properties remain present.
7. Check browser Network and Console panels for CORS, mixed-content, or JavaScript errors.

## 7. Rollback approach

Do not improvise a destructive rollback. Identify whether the failure is code, configuration, or schema related.

### Frontend-only failure

Re-deploy the previously known-good Git commit, rebuild, and restart PM2. The `.env` file remains server-specific and should not be replaced by Git.

### Backend code failure without an incompatible database change

Build the previously known-good commit, copy its JAR to `/opt/shagriha/backend/app.jar`, and restart `shagriha-backend`.

### Migration or data failure

Stop application writes and assess the migration before restoring anything. Restoring a PostgreSQL dump replaces database state and can discard data written since the backup. A database restore requires an explicit recovery plan and maintenance window.

Useful diagnostics:

```bash
systemctl status shagriha-backend --no-pager
journalctl -u shagriha-backend -n 200 --no-pager
pm2 list
pm2 logs shagriha-frontend --lines 100 --nostream
apache2ctl -S
apache2ctl configtest
ss -ltnp | grep -E ':(3000|8080|5432)[[:space:]]'
```

## 8. Production cutover preparation

The September 1 deployment did not modify the old live server or its DNS. Before moving `shagriha.com` or `www.shagriha.com`:

1. Inventory the old production application, proxy, database, uploads, OAuth configuration, and TLS certificates.
2. Decide whether production will use the existing database, a migrated database, or a new clean database.
3. Back up and validate the live database immediately before cutover.
4. Never import the local development database wholesale.
5. Plan how new writes are prevented during final database synchronization.
6. Configure and test new Apache virtual hosts without disrupting sandbox.
7. Add production frontend origins and redirect URIs to Google Cloud before cutover.
8. Reduce DNS TTL in advance where possible.
9. Define health checks, acceptance tests, rollback criteria, and responsible people.
10. Change DNS only after the new production path passes tests using explicit Host headers or a local hosts-file override.
11. Monitor application logs, HTTP status, OAuth, and database activity after cutover.
12. Keep the old server available for a defined rollback period.

DNS changes do not copy data. Reverse proxies do not synchronize databases. Code, configuration, DNS, and data migration must be treated as separate workstreams.

## 9. Security follow-up

The following items should be addressed before production cutover:

- PostgreSQL currently listens on public interfaces at port `5432`. Bind it to localhost/private interfaces and block public access in the VPS firewall.
- Rotate the VPS root password that appeared in deployment documentation and redact that document.
- Prefer a non-root deployment user with limited permissions.
- Restrict Mapbox tokens to approved origins and minimum scopes.
- Keep Google client secrets, database passwords, and backup dumps outside Git.
- Review the six high-severity npm audit findings in a development branch; do not auto-fix them on the VPS.
- Add persistent JWT signing keys or a secrets manager. The current in-memory signing key invalidates access tokens when the backend restarts.
- Add centralized backup retention, restore testing, monitoring, and alerting.
- Consider binding Next.js and Spring Boot explicitly to localhost so only Apache can reach them.

## 10. Skills to learn

### Foundation

1. Linux shell navigation, processes, permissions, logs, and package management.
2. Git branches, remote tracking, fast-forward pulls, status inspection, commits, tags, and rollback concepts.
3. HTTP fundamentals: methods, status codes, headers, cookies, redirects, CORS, and TLS.
4. DNS fundamentals: A/AAAA/CNAME records, TTL, propagation, apex versus `www`, and rollback timing.

### Application deployment

5. Next.js production builds, build-time environment variables, PM2, server/client rendering, and stale browser assets.
6. Java 21, Maven Wrapper, Spring Boot profiles, external configuration, Actuator, and executable JARs.
7. systemd unit files, service lifecycle, boot persistence, graceful shutdown, and journal inspection.
8. Apache virtual hosts, reverse proxy headers, TLS termination, Certbot, and configuration testing.

### Data and authentication

9. PostgreSQL roles, authentication, backup/restore, transactions, constraints, and access control.
10. PostGIS installation, spatial types, indexes, and version compatibility.
11. Flyway migration design, forward-only schema evolution, migration validation, and data-migration safety.
12. OAuth 2.0/OpenID Connect, authorized origins, exact redirect URIs, state/session security, and provider troubleshooting.
13. JWT issuance, validation, expiration, key persistence, and rotation.

### Production operations

14. Secret management and separation of code from environment-specific configuration.
15. Linux and cloud firewalls, least privilege, SSH keys, patching, and exposed-port auditing.
16. Release checklists, smoke tests, backups, rollback drills, maintenance windows, and incident response.
17. CI/CD pipelines that build and test artifacts before the server receives them.
18. Observability: structured logs, metrics, uptime monitoring, alerts, and error tracking.

## 11. Suggested practice sequence

1. Reproduce the stack on a local VM or disposable VPS.
2. Deploy a trivial Next.js page behind Apache and PM2.
3. Deploy a trivial Spring Boot health endpoint under systemd.
4. Add PostgreSQL/PostGIS and practice `pg_dump` and `pg_restore` into a disposable database.
5. Add a harmless Flyway migration and verify its history.
6. Configure a test subdomain and TLS certificate.
7. Practice OAuth with a separate test client.
8. Simulate a failed frontend release and roll it back.
9. Simulate a failed backend release without changing the database.
10. Practice restoring a database backup in an isolated environment.
11. Automate repeatable checks in CI/CD.
12. Attempt the live cutover only after writing and reviewing a complete runbook and rollback plan.

## 12. Useful service commands

```bash
# Backend
systemctl status shagriha-backend --no-pager
systemctl restart shagriha-backend
journalctl -u shagriha-backend -f

# Frontend
pm2 list
pm2 restart shagriha-frontend --update-env
pm2 logs shagriha-frontend
pm2 save

# Apache
apache2ctl configtest
apache2ctl -S
systemctl reload apache2

# Database
pg_lsclusters
sudo -u postgres psql -P pager=off -d shagriha_db

# Ports
ss -ltnp | grep -E ':(3000|8080|5432)[[:space:]]'
```
