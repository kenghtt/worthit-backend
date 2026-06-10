# worthit-backend

Minimal Spring Boot starter for the **WorthIt** backend.

This project was bootstrapped from the `snap-vault-backend` template, keeping only the
generic, reusable infrastructure pieces (project layout, security/JWT scaffolding,
exception handling, CORS, validation, logging, Dockerfile, basic tests). No business
logic, domain entities, or product-specific code from the source project was carried over.

> Look for `TODO(worthIt)` markers throughout the codebase — those are the seams where
> real WorthIt features should be plugged in.

---

## Stack

- Java 17
- Spring Boot 3.5.6
  - `spring-boot-starter-web`
  - `spring-boot-starter-security`
  - `spring-boot-starter-oauth2-resource-server` (JWT validation, wired but disabled by default)
  - `spring-boot-starter-validation`
- Lombok
- Maven (with wrapper: `./mvnw`)

## Project layout

```
src/main/java/com/worthit/backend
├── WorthitBackendApplication.java   # @SpringBootApplication entry point
├── config
│   ├── SecurityConfig.java          # JWT chain — active when security.auth.enabled=true
│   └── SecurityConfigLocal.java     # Permissive chain — active when security.auth.enabled=false (default)
├── controller
│   └── HelloController.java         # GET /api/hello smoke test
└── exception
    ├── ApiErrorResponse.java        # Generic error envelope
    ├── GlobalExceptionHandler.java  # @RestControllerAdvice for common failures + bean validation
    ├── ResourceNotFoundException.java
    └── UnauthorizedException.java
```

## Configuration

- `application.properties` — defaults (auth **disabled**, profile = `local`).
- `application-local.properties` — local-dev overrides.
- `application-prod.properties` — production overrides; reads JWT/CORS values from environment variables.
- `secrets.properties` — *optional*, git-ignored, loaded if present.

Key properties:

| Property | Purpose |
| --- | --- |
| `security.auth.enabled` | `false` (default) uses `SecurityConfigLocal`; `true` activates JWT validation via `SecurityConfig`. |
| `security.jwt.issuer` | Expected token issuer (when JWT is enabled). |
| `security.jwt.jwks-uri` | JWKS endpoint for asymmetric (RS256/ES256) signature validation. |
| `security.jwt.expected-audience` | Optional audience claim check. |
| `security.jwt.secret` | HS256 shared secret (legacy/dev fallback). |
| `app.security.cors.allowed-origins` | Comma-separated allowed CORS origins. |

## Running locally

```bash
./mvnw spring-boot:run
```

Then hit the smoke-test endpoint:

```bash
curl http://localhost:8080/api/hello
# {"app":"worthit-backend","status":"ok","message":"Hello from WorthIt backend"}
```

## Running tests

```bash
./mvnw test
```

The basic test suite verifies that the Spring context boots and that `/api/hello`
returns the expected JSON.

## Docker

```bash
docker build -t worthit-backend .
docker run --rm -p 8080:8080 worthit-backend
```

For production deployments, set `SPRING_PROFILES_ACTIVE=prod` and inject the auth
configuration via environment variables (`SECURITY_JWT_ISSUER`, `SECURITY_JWT_JWKS_URI`,
`SECURITY_JWT_EXPECTED_AUDIENCE`, `APP_SECURITY_CORS_ALLOWED_ORIGINS`).

## What to build next

- [ ] Decide on auth provider (e.g. Supabase, Auth0, Cognito) and fill in
      `security.jwt.*` + adjust `SignatureAlgorithm` in `SecurityConfig#jwtDecoder()`.
- [ ] Define worthIt domain modules (entities, repositories, services, controllers).
- [ ] Add persistence (`spring-boot-starter-data-jpa` + driver) when a database is chosen.
- [ ] Add an actuator dependency if you want `/actuator/health` (already pre-permitted in `SecurityConfig`).
- [ ] Expand the test suite alongside new features.
# worthit-backend
