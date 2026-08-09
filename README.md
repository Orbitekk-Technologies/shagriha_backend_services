# Shagriha backend services

Spring Boot 4 / Java 21 REST API foundation for the Shagriha rental platform.

## Included

- PostgreSQL 17 with PostGIS and Flyway-managed schema
- Local tenant/manager signup and login
- RSA-signed short-lived JWT access tokens
- Role-based endpoint protection and frontend-scoped CORS
- Google OAuth2/OIDC client profile wiring
- Initial relational model for profiles, properties, spatial locations, favorites,
  applications, leases, payments, and tokenized payment-method metadata
- Actuator health probes

The project deliberately does not store card numbers or security codes. A payment
provider must tokenize cards in the browser before payment-method endpoints are built.

## Run locally

Requirements: Java 21+ and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

The API base URL is `http://localhost:8080/api/v1` and health is available at
`GET /api/v1/actuator/health`.

To enable Google login, set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and
`SPRING_PROFILES_ACTIVE=oauth`. Register this callback with Google:

```text
http://localhost:8080/api/v1/login/oauth2/code/google
```

## Implemented API

- `POST /auth/signup`
- `POST /auth/login`
- `GET /auth/me`
- `GET|PATCH /tenants/me` (legacy-compatible `GET|PUT /tenants/{userId}`)
- `GET /tenants/me/favorites`
- `POST|DELETE /tenants/me/favorites/{propertyId}`
- `GET /tenants/me/residences`
- `GET|POST /applications`
- `PUT /applications/{id}/status`
- `GET /leases`
- `GET /leases/{id}/payments`
- `GET /oauth2/authorization/google` when the `oauth` profile is active

All protected requests use `Authorization: Bearer <access-token>`.

## Next milestones

1. Refresh-token rotation and Google-user provisioning/success handling
2. Property multipart uploads, geocoding, and PostGIS search
3. Payment-method token storage
4. Payment-provider integration and webhooks

Run verification with:

```bash
./mvnw test
```
