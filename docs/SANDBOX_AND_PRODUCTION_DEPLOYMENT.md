# Sandbox and Production Deployment Guide

Last verified: September 17, 2026

This guide documents the current Shagriha release process for both sandbox and
production. It records what was done for the September 17 release, why the
steps were performed in that order, and which Git and Linux commands are used.
It intentionally contains no passwords, OAuth secrets, or API tokens.

## 1. Current architecture

Both environments run on VPS `212.28.189.194`, but they use separate
applications, ports, configuration files, and databases.

| Environment | Component | Public address | Local target | Process |
| --- | --- | --- | --- | --- |
| Sandbox | Frontend | `https://sandbox.shagriha.com` | `127.0.0.1:3000` | PM2 `shagriha-frontend` |
| Sandbox | API | `https://api-sandbox.shagriha.com` | port `8080` | systemd `shagriha-backend` |
| Sandbox | Database | Not public | `shagriha_db` | PostgreSQL/PostGIS |
| Production | Frontend | `https://www.shagriha.com` | `127.0.0.1:3001` | PM2 `shagriha-production-frontend` |
| Production | API | `https://api.shagriha.com` | port `8081` | systemd `shagriha-production-backend` |
| Production | Database | Not public | `shagriha_prod` | PostgreSQL/PostGIS |

The bare production domain `https://shagriha.com` redirects permanently to
`https://www.shagriha.com`. Apache terminates TLS and proxies each hostname to
the appropriate local process.

Application paths:

```text
Sandbox frontend source:      /root/shagriha/frontend
Sandbox backend source:       /root/shagriha/backend
Sandbox backend JAR:          /opt/shagriha/backend/app.jar

Production frontend source:   /opt/shagriha/production/frontend
Production backend artifacts: /opt/shagriha/production/backend
Production backend JAR:       /opt/shagriha/production/backend/app.jar
```

The production backend directory is an artifact directory, not a Git checkout.
The backend is built and tested once, deployed to sandbox, validated, and then
the same JAR is copied to production. This prevents sandbox and production from
running binaries built from different source states.

## 2. Why database releases require a stricter process

A code-only release can normally be rolled back by restoring the previous JAR
or frontend build. A release containing Flyway migrations also changes the
database schema. Git can restore source code, but Git cannot restore database
rows or reverse a schema migration safely.

The September 17 release introduced:

```text
V9  - application desired move-in date
V10 - half-bathroom support
V11 - property lease documents
V12 - property listed-by field
```

For this reason, the deployment order was:

1. Verify which server and virtual hosts are active.
2. Inspect Git state and incoming commits.
3. Inspect current Flyway history and pending migrations.
4. Back up and validate the sandbox database and current artifacts.
5. Deploy the backend to sandbox and let Flyway apply migrations.
6. Deploy the sandbox frontend.
7. Perform HTTP, CORS, OAuth, and manual browser smoke tests.
8. Back up and validate the production database and artifacts separately.
9. Deploy the exact sandbox-tested backend JAR to production.
10. Deploy the production frontend and run final checks.

Never restore a database merely because a deployment is starting.
`pg_restore --list` only validates that a backup archive is readable;
`pg_restore` without `--list` is a recovery operation and can replace data.

## 3. How Git was used for the September 17 release

Feature work was committed to feature branches and merged through GitHub:

```text
Backend feature commit: cef18bb
Backend merged release: 911556f

Frontend feature commit: f48df071
Frontend merged release: 49d2046f
```

The backend release branch
`release/sandbox-production-2026-09-17` was created from the merged backend
commit. The servers continued to pull the established deployment branches:

```text
Backend:  origin/feature/springboot-api-foundation
Frontend: origin/main
```

Important concepts:

- `git fetch --prune origin` updates knowledge of remote branches without
  changing the checked-out files.
- `git log HEAD..origin/<branch>` shows commits that would be deployed.
- `git diff --name-status HEAD..origin/<branch>` shows affected files.
- `git pull --ff-only` moves the server checkout forward only when no merge
  commit or history rewrite is required. It fails safely if histories diverge.
- A feature branch is not deployed merely because it exists. Its commits must
  be merged into the branch that the server deploys, or an explicit release
  artifact must be selected.
- Uncommitted developer files are not included in a deployment. The September
  17 production frontend came from `origin/main` at `49d2046f`.

Before any pull, run:

```bash
git -C <repository> status --short --branch
```

Stop if tracked files have unexpected modifications. Do not use
`git reset --hard` to hide an unexplained server change.

## 4. Phase A: discover the active infrastructure

The old production server was previously documented as `178.18.242.24`.
Before deploying, DNS, Apache, ports, and processes were inspected rather than
assuming that the old layout was still current.

```bash
hostname
hostname -I
apache2ctl -S
grep -RHE 'ServerName|ServerAlias|ProxyPass' /etc/apache2/sites-enabled/
pm2 list
systemctl list-units --type=service --all | grep -i shagriha
ss -ltnp | grep -E ':(3000|3001|8080|8081)[[:space:]]'
ps -ef | grep '[j]ava'
```

These checks established that both environments now run on `212.28.189.194`
and are separated by ports, directories, services, and databases. DNS alone
does not reveal which local service or database a hostname ultimately uses.

## 5. Phase B: local release preparation

Run backend tests before publishing or deploying:

```bash
./mvnw test
```

Fetch and confirm that the merged commits exist:

```bash
git fetch origin --prune
git log --oneline --decorate -10 origin/feature/springboot-api-foundation
git log --oneline --decorate -10 origin/main
```

Do not deploy an uncommitted working-tree change. A safe feature workflow is:

```bash
git switch -c feature/descriptive-name
git add <files>
git commit -m "feat: describe the change"
git push --set-upstream origin feature/descriptive-name
```

Open a pull request into the actual deployment branch, wait for checks/review,
and merge it before starting the server deployment.

## 6. Phase C: sandbox pre-deployment checks

Connect and verify health before changing anything:

```bash
ssh root@212.28.189.194
df -h /
systemctl status shagriha-backend --no-pager
pm2 list
git -C /root/shagriha/backend status --short --branch
git -C /root/shagriha/frontend status --short --branch
curl --fail http://127.0.0.1:8080/api/v1/actuator/health
curl --fail -I http://127.0.0.1:3000
```

Fetch and inspect without changing the checkout:

```bash
git -C /root/shagriha/backend fetch --prune origin
git -C /root/shagriha/backend --no-pager log \
  --oneline HEAD..origin/feature/springboot-api-foundation
git -C /root/shagriha/backend diff \
  --name-status HEAD..origin/feature/springboot-api-foundation

git -C /root/shagriha/frontend fetch --prune origin
git -C /root/shagriha/frontend --no-pager log \
  --oneline HEAD..origin/main
```

Check the database migration baseline:

```bash
sudo -u postgres psql -d shagriha_db -c \
  'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 12;'
```

Review every new migration file. Never edit a migration already recorded in
`flyway_schema_history`; add a new version instead.

## 7. Phase D: sandbox backup

Use a new dated directory for every deployment:

```bash
mkdir -p /root/deployment-backup-YYYY-MM-DD/sandbox
chmod 700 /root/deployment-backup-YYYY-MM-DD/sandbox

sudo -u postgres pg_dump -Fc shagriha_db \
  -f /var/lib/postgresql/shagriha_db-before-release.dump
mv /var/lib/postgresql/shagriha_db-before-release.dump \
  /root/deployment-backup-YYYY-MM-DD/sandbox/
chmod 600 \
  /root/deployment-backup-YYYY-MM-DD/sandbox/shagriha_db-before-release.dump

cp /opt/shagriha/backend/app.jar \
  /root/deployment-backup-YYYY-MM-DD/sandbox/backend-app.jar
cp /root/shagriha/backend/.env.local \
  /root/deployment-backup-YYYY-MM-DD/sandbox/backend-env.backup
cp /root/shagriha/frontend/.env \
  /root/deployment-backup-YYYY-MM-DD/sandbox/frontend-env.backup
chmod 600 /root/deployment-backup-YYYY-MM-DD/sandbox/*
```

Validate the archive without restoring it:

```bash
pg_restore --list \
  /root/deployment-backup-YYYY-MM-DD/sandbox/shagriha_db-before-release.dump \
  | head -20
```

The dump contains application data and the environment backups contain
secrets. Keep this directory root-only and outside Git.

## 8. Phase E: deploy sandbox backend

Fast-forward the checkout and build while the existing service stays online:

```bash
git -C /root/shagriha/backend pull --ff-only \
  origin feature/springboot-api-foundation

cd /root/shagriha/backend
./mvnw clean package -DskipTests
```

Tests are skipped only on the VPS to avoid tests accidentally using a deployed
database. The same commit must already have passed tests locally or in CI.

After `BUILD SUCCESS`, install the JAR and restart sandbox:

```bash
install -m 0644 \
  target/shagriha-backend-services-0.0.1-SNAPSHOT.jar \
  /opt/shagriha/backend/app.jar

systemctl restart shagriha-backend
systemctl status shagriha-backend --no-pager -l
journalctl -u shagriha-backend --since "10 minutes ago" --no-pager -l
curl --fail http://127.0.0.1:8080/api/v1/actuator/health
```

Confirm that all new migrations succeeded:

```bash
sudo -u postgres psql -d shagriha_db -c \
  'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 12;'
```

Spring Boot runs Flyway before the application becomes ready. A failed
migration normally prevents startup, which is why the journal and health check
are both required.

## 9. Phase F: deploy sandbox frontend

The sandbox environment file must contain:

```properties
NEXT_PUBLIC_API_BASE_URL=https://api-sandbox.shagriha.com/api/v1/
```

`NEXT_PUBLIC_*` values are compiled into the browser bundle, so inspect the
file before building and never copy production and sandbox `.env` files over
each other.

```bash
git -C /root/shagriha/frontend pull --ff-only origin main
cd /root/shagriha/frontend
npm ci
npm run build
```

Do not run `npm audit fix` on a server. Dependency remediation belongs in a
reviewed development branch.

After a successful build:

```bash
pm2 restart shagriha-frontend --update-env
pm2 save
pm2 list
pm2 logs shagriha-frontend --lines 80 --nostream
```

## 10. Phase G: verify sandbox

```bash
curl --fail -I https://sandbox.shagriha.com
curl --fail -I https://sandbox.shagriha.com/search
curl --fail -I https://sandbox.shagriha.com/managers/newproperty
curl --fail https://api-sandbox.shagriha.com/api/v1/actuator/health
```

CORS must be tested against the sandbox API hostname, not the production API:

```bash
curl --silent --show-error --dump-header - --output /dev/null \
  --request OPTIONS \
  https://api-sandbox.shagriha.com/api/v1/auth/me \
  --header 'Origin: https://sandbox.shagriha.com' \
  --header 'Access-Control-Request-Method: GET' \
  --header 'Access-Control-Request-Headers: Authorization'
```

Expected:

```text
HTTP 200
Access-Control-Allow-Origin: https://sandbox.shagriha.com
```

Perform manual tests in a private browser:

1. Password and Google sign-in.
2. Property search and details.
3. Manager property create/edit, including half bathrooms and listed-by.
4. Tenant application with desired move-in date.
5. Manager application approval/rejection.
6. Lease PDF upload and download.
7. Browser Network and Console checks for CORS and HTTP errors.

Canceled Mapbox `.vector.pbf` requests can be normal when the viewport changes
and obsolete tiles are replaced. Investigate only if tiles fail to load, the
map remains blank, or uncanceled requests return errors.

Do not proceed to production until sandbox smoke tests pass.

## 11. Phase H: production discovery and backup

Verify production paths and services rather than assuming they match sandbox:

```bash
systemctl cat shagriha-production-backend
systemctl status shagriha-production-backend --no-pager -l
pm2 describe shagriha-production-frontend
curl --fail http://127.0.0.1:8081/api/v1/actuator/health
```

Verify only non-secret configuration values:

```bash
grep -E '^(DATABASE_URL|DATABASE_USERNAME|FRONTEND_URL|PORT|GOOGLE_REDIRECT_URI|JWT_ISSUER)=' \
  /opt/shagriha/production/backend/.env.local
grep -E '^(NEXT_PUBLIC_API_BASE_URL|NEXT_PUBLIC_DEMO_MODE)=' \
  /opt/shagriha/production/frontend/.env
```

Expected production routing:

```text
FRONTEND_URL=https://www.shagriha.com
PORT=8081
NEXT_PUBLIC_API_BASE_URL=https://api.shagriha.com/api/v1/
```

Create and validate a separate production backup:

```bash
mkdir -p /root/deployment-backup-YYYY-MM-DD/production
chmod 700 /root/deployment-backup-YYYY-MM-DD/production

sudo -u postgres pg_dump -Fc shagriha_prod \
  -f /var/lib/postgresql/shagriha_prod-before-release.dump
mv /var/lib/postgresql/shagriha_prod-before-release.dump \
  /root/deployment-backup-YYYY-MM-DD/production/

cp /opt/shagriha/production/backend/app.jar \
  /root/deployment-backup-YYYY-MM-DD/production/backend-app.jar
cp /opt/shagriha/production/backend/.env.local \
  /root/deployment-backup-YYYY-MM-DD/production/backend-env.backup
cp /opt/shagriha/production/frontend/.env \
  /root/deployment-backup-YYYY-MM-DD/production/frontend-env.backup
git -C /opt/shagriha/production/frontend rev-parse HEAD \
  > /root/deployment-backup-YYYY-MM-DD/production/frontend-commit.txt

tar -C /opt/shagriha/production/frontend -czf \
  /root/deployment-backup-YYYY-MM-DD/production/frontend-build.tar.gz \
  .next package.json package-lock.json

chmod 600 /root/deployment-backup-YYYY-MM-DD/production/*
pg_restore --list \
  /root/deployment-backup-YYYY-MM-DD/production/shagriha_prod-before-release.dump \
  | head -20
```

## 12. Phase I: deploy production backend

Deploy the exact JAR already proven in sandbox. Compare checksums before and
after copying:

```bash
sha256sum \
  /opt/shagriha/backend/app.jar \
  /opt/shagriha/production/backend/app.jar

install -m 0750 \
  /opt/shagriha/backend/app.jar \
  /opt/shagriha/production/backend/app.jar

sha256sum \
  /opt/shagriha/backend/app.jar \
  /opt/shagriha/production/backend/app.jar
```

The two hashes must match after installation. Then restart only production:

```bash
systemctl restart shagriha-production-backend
systemctl status shagriha-production-backend --no-pager -l
curl --fail http://127.0.0.1:8081/api/v1/actuator/health
journalctl -u shagriha-production-backend \
  --since "10 minutes ago" --no-pager -l
```

Verify production Flyway history separately:

```bash
sudo -u postgres psql -d shagriha_prod -c \
  'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 12;'
```

Backend restarts currently invalidate existing access tokens because signing
keys are generated in memory. Plan for users to sign in again.

## 13. Phase J: deploy production frontend

```bash
git -C /opt/shagriha/production/frontend fetch --prune origin
git -C /opt/shagriha/production/frontend --no-pager log \
  --oneline HEAD..origin/main
git -C /opt/shagriha/production/frontend pull --ff-only origin main

cd /opt/shagriha/production/frontend
npm ci
npm run build
```

After a successful build:

```bash
pm2 restart shagriha-production-frontend --update-env
pm2 save
pm2 list
pm2 logs shagriha-production-frontend --lines 80 --nostream
```

## 14. Phase K: final production verification

```bash
curl --head https://shagriha.com
curl --fail --head https://www.shagriha.com
curl --fail --head https://www.shagriha.com/search
curl --fail --head https://www.shagriha.com/managers/newproperty
curl --fail https://api.shagriha.com/api/v1/actuator/health
```

The bare domain should return `308` to `https://www.shagriha.com`; application
pages should return `200`.

Production CORS:

```bash
curl --silent --show-error --dump-header - --output /dev/null \
  --request OPTIONS \
  https://api.shagriha.com/api/v1/auth/me \
  --header 'Origin: https://www.shagriha.com' \
  --header 'Access-Control-Request-Method: GET' \
  --header 'Access-Control-Request-Headers: Authorization'
```

Production OAuth:

```bash
curl --silent --show-error --head \
  https://api.shagriha.com/api/v1/oauth2/authorization/google
```

OAuth should return `302`, and its `Location` header must contain the production
callback:

```text
https://api.shagriha.com/api/v1/login/oauth2/code/google
```

Complete a short production browser smoke test after the automated checks.

## 15. Rollback principles

### Frontend failure

Restore or rebuild the recorded previous frontend commit, then restart only the
affected PM2 process. Preserve the environment-specific `.env` file.

### Backend code failure without a migration incompatibility

Restore the previous environment-specific JAR and restart only its systemd
service. Do not copy sandbox configuration into production.

### Migration or data failure

Stop application writes and assess the failure before restoring anything.
Restoring a dump can discard every write made after that dump. A database
restore requires an explicit recovery plan and maintenance window.

Do not automatically run reverse SQL and do not edit an applied Flyway file.

## 16. Operational follow-ups

- Bind or firewall backend ports `8080` and `8081` so only Apache/local traffic
  can reach them. Bind the sandbox frontend port `3000` locally as well.
- Review npm vulnerabilities in a development branch; never auto-fix the live
  server.
- Add persistent JWT signing keys so restarts do not invalidate sessions.
- Move from root-run services to limited deployment users.
- Automate tests, artifact creation, database-backup validation, deployment,
  and smoke checks in CI/CD.
- Add monitoring, alerting, backup retention, and periodic restore tests.

