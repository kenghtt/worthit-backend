# WorthIt — Backend Specification (Spring Boot)

This document is the **single source of truth** for building the WorthIt
backend. It defines the database schema, HTTP API surface, validation
rules, security posture, and operational concerns. It is self-contained:
it does **not** depend on any other document and does **not** describe
any frontend, UI, or design-tool specifics.

> Conventions
> - SQL examples target **PostgreSQL 15+** (recommended). Portable to MySQL.
> - Primary keys are `BIGSERIAL` unless noted.
> - Every table has `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` and
>   `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` (omitted in column
>   tables below for brevity; apply a trigger or `@PreUpdate` to keep
>   `updated_at` current).
> - Money is stored in **whole US dollars** as `INTEGER`.
> - "Worth Score" and "Stress" are `NUMERIC(3,1)` in the range 0.0–10.0.
> - All timestamps are UTC (`TIMESTAMPTZ`).
> - All endpoints live under `/api/v1/...`, accept and return JSON, and
>   are **public / unauthenticated** in v1.

---

## 1. High-level model

The core unit of data is an **Experience**: one anonymous, self-reported
review of a single `(company, role, location)` triple. Every other table
is either a **dimension** (Company, Role, Location, Level) referenced by
an Experience, or a **derived aggregate** computed from Experiences.

### Tables (5 total)

| # | Table         | Purpose                                        |
|---|---------------|------------------------------------------------|
| 1 | `companies`   | Canonical list of companies                    |
| 2 | `locations`   | Canonical list of cities (city + state)        |
| 3 | `roles`       | Canonical list of job role titles              |
| 4 | `levels`      | Per-company level ladder (SDE II, L4, E5, ...) |
| 5 | `experiences` | The main fact table — one submitted review     |

### Entity relationships

```
experiences ──► companies ──► (averages computed)
  │  │   ┌────► roles
  │  └───┤
  │      └───► levels (optional, scoped to company)
  └────────► locations
```

There are **no user-scoped tables** in v1 because there are no user
accounts. All submissions are anonymous.

---

## 2. Table-by-table schema

### 2.1 `companies`

Canonical company list. The slug is what appears in public URLs.

| Column         | Type      | Constraints           | Notes                            |
|----------------|-----------|-----------------------|----------------------------------|
| `id`           | BIGSERIAL | PRIMARY KEY           |                                  |
| `slug`         | TEXT      | UNIQUE NOT NULL       | URL segment, e.g. `amazon`       |
| `name`         | TEXT      | NOT NULL              | Display name, e.g. `Amazon`      |
| `website_url`  | TEXT      | NULL                  |                                  |
| `industry`     | TEXT      | NULL                  | `Tech`, `Finance`, `Retail`, ... |
| `headquarters` | TEXT      | NULL                  | Free-form, e.g. `Seattle, WA`    |
| `description`  | TEXT      | NULL                  | Short blurb                      |
| `is_active`    | BOOLEAN   | NOT NULL DEFAULT TRUE | Soft delete / hide               |

**Indexes:**

- `UNIQUE (slug)`
- `INDEX (name)` for autocomplete / search

---

### 2.2 `locations`

Canonical city list (US-only in v1).

| Column      | Type      | Constraints           | Notes                             |
|-------------|-----------|-----------------------|-----------------------------------|
| `id`        | BIGSERIAL | PRIMARY KEY           |                                   |
| `slug`      | TEXT      | UNIQUE NOT NULL       | e.g. `seattle`, `new-york`        |
| `city`      | TEXT      | NOT NULL              | e.g. `Seattle`                    |
| `state`     | TEXT      | NOT NULL              | 2-letter US state code, e.g. `WA` |
| `is_active` | BOOLEAN   | NOT NULL DEFAULT TRUE |                                   |

**Indexes:**

- `UNIQUE (slug)`
- `UNIQUE (city, state)` — prevent duplicates (e.g. Springfield, MA vs MO)
- `INDEX (city)` for search

Compute `display_name` as `city || ', ' || state` at read time.

---

### 2.3 `roles`

Canonical role list (titles, not levels). Roles are **global** — the
same `Software Engineer` row is referenced by every company.

| Column      | Type      | Constraints           | Notes                                |
|-------------|-----------|-----------------------|--------------------------------------|
| `id`        | BIGSERIAL | PRIMARY KEY           |                                      |
| `slug`      | TEXT      | UNIQUE NOT NULL       | e.g. `software-engineer`             |
| `name`      | TEXT      | NOT NULL              | e.g. `Software Engineer`             |
| `family`    | TEXT      | NULL                  | `Engineering` / `Product` / `Design` |
| `is_active` | BOOLEAN   | NOT NULL DEFAULT TRUE |                                      |

**Indexes:** `UNIQUE (slug)`

---

### 2.4 `levels`

A **level** is the company-specific job-ladder rung within a role.
Different companies use different names for the same underlying
seniority:

- Amazon SWE ladder: `SDE I`, `SDE II`, `SDE III`, `Principal SDE`
- Google SWE ladder: `L3`, `L4`, `L5`, `L6`, `L7`
- Meta SWE ladder:   `E3`, `E4`, `E5`, `E6`, `E7`
- Microsoft SWE:     `SWE`, `SWE II`, `Senior SWE`, `Principal SWE`

Levels are therefore scoped to a `company_id`.

| Column            | Type      | Constraints                                         | Notes                              |
|-------------------|-----------|-----------------------------------------------------|------------------------------------|
| `id`              | BIGSERIAL | PRIMARY KEY                                         |                                    |
| `company_id`      | BIGINT    | NOT NULL REFERENCES companies(id) ON DELETE CASCADE |                                    |
| `role_id`         | BIGINT    | NULL REFERENCES roles(id) ON DELETE SET NULL        | Optional: scope to one role family |
| `name`            | TEXT      | NOT NULL                                            | e.g. `SDE II`, `L4`, `E5`          |
| `normalized_rank` | INTEGER   | NOT NULL                                            | Sort order within the ladder       |
| `is_active`       | BOOLEAN   | NOT NULL DEFAULT TRUE                               |                                    |

**Indexes:**

- `UNIQUE (company_id, role_id, name)`
- `INDEX (company_id, normalized_rank)`

> **Pragmatic alternative.** You may skip this table on day 1 and store
> the level as `experiences.level_name` only. The trade-off is free-text
> drift (`SDE II` vs `SDE2` vs `SDE 2`). The lookup table is worthwhile
> once you aggregate by level.

---

### 2.5 `experiences` (main table)

One row per submitted review.

| Column              | Type         | Constraints                                                                      | Notes                                       |
|---------------------|--------------|----------------------------------------------------------------------------------|---------------------------------------------|
| `id`                | BIGSERIAL    | PRIMARY KEY                                                                      |                                             |
| `company_id`        | BIGINT       | NOT NULL REFERENCES companies(id) ON DELETE RESTRICT                             |                                             |
| `role_id`           | BIGINT       | NOT NULL REFERENCES roles(id) ON DELETE RESTRICT                                 |                                             |
| `custom_role`       | TEXT         | NULL                                                                             | Free text when role is `other`              |
| `level_id`          | BIGINT       | NULL REFERENCES levels(id) ON DELETE SET NULL                                    | Optional mapped level                       |
| `level_name`        | TEXT         | NULL                                                                             | Raw level text fallback                     |
| `location_id`       | BIGINT       | NOT NULL REFERENCES locations(id) ON DELETE RESTRICT                             |                                             |
| `employment_status` | TEXT         | NOT NULL CHECK (employment_status IN ('current','past'))                         |                                         |
| `years_experience`  | SMALLINT     | NOT NULL CHECK (years_experience BETWEEN 0 AND 60)                               | Total industry experience                   |
| `years_at_company`  | SMALLINT     | NULL CHECK (years_at_company BETWEEN 0 AND 60)                                   |                                             |
| `base_salary`       | INTEGER      | NOT NULL CHECK (base_salary >= 0)                                                | Whole USD                                   |
| `bonus`             | INTEGER      | NOT NULL DEFAULT 0 CHECK (bonus >= 0)                                            | Whole USD                                   |
| `stock`             | INTEGER      | NOT NULL DEFAULT 0 CHECK (stock >= 0)                                            | Annualized equity, whole USD                |
| `signing_bonus`     | INTEGER      | NOT NULL DEFAULT 0 CHECK (signing_bonus >= 0)                                    | Whole USD                                   |
| `compensation_year` | SMALLINT     | NOT NULL CHECK (compensation_year BETWEEN 2000 AND 2100)                         |                                         |
| `stress_level`      | NUMERIC(3,1) | NOT NULL CHECK (stress_level BETWEEN 0 AND 10)                                   |                                             |
| `hours_per_week`    | SMALLINT     | NOT NULL CHECK (hours_per_week BETWEEN 0 AND 120)                                |                                             |
| `worth_it_score`    | NUMERIC(3,1) | NOT NULL CHECK (worth_it_score BETWEEN 0 AND 10)                                 |                                             |
| `wish_knew`         | TEXT         | NULL                                                                             | Free text, "what do you wish you knew"      |
| `extras`            | JSONB        | NULL                                                                             | Open bag for optional future fields         |
| `status`            | TEXT         | NOT NULL DEFAULT 'published' CHECK (status IN ('pending','published','removed')) | Moderation state |
| `submitter_ip_hash` | TEXT         | NULL                                                                             | SHA-256 of submitter IP (for rate limiting) |
| `user_agent`        | TEXT         | NULL                                                                             | Truncated, for abuse forensics              |

**Derived helpers (computed on read):**

- `total_comp = base_salary + bonus + stock + signing_bonus`
- `display_location = locations.city || ', ' || locations.state`

**Indexes:**

- `INDEX (company_id, role_id)`
- `INDEX (company_id, location_id)`
- `INDEX (location_id)`
- `INDEX (status) WHERE status = 'published'` (partial)
- `INDEX (created_at DESC)` for "recent submissions"

> **`extras` JSONB.** Reserved for optional future submission fields
> (e.g. work breakdown percentages, manager-quality ratings,
> why-stay/why-leave tags). Storing them in JSONB avoids migrations as
> the form evolves.

---

## 3. Derived / aggregated data

List and detail endpoints need aggregates such as `avg_worth_score`,
`experience_count`, `avg_base_salary`, etc.

**Recommendation:** Do **not** denormalize aggregates onto `companies` /
`locations`. Compute them from `experiences`.

### Option A — Postgres materialized views (recommended)

```sql
CREATE MATERIALIZED VIEW company_stats AS
SELECT
  c.id                                 AS company_id,
  c.slug                               AS slug,
  COUNT(e.id)                          AS experience_count,
  AVG(e.worth_it_score)::NUMERIC(3,1)  AS avg_worth_score,
  AVG(e.base_salary)::INTEGER          AS avg_base_salary,
  AVG(e.bonus)::INTEGER                AS avg_bonus,
  AVG(e.stock)::INTEGER                AS avg_stock,
  AVG(e.stress_level)::NUMERIC(3,1)    AS avg_stress,
  AVG(e.hours_per_week)::NUMERIC(4,1)  AS avg_hours
FROM companies c
LEFT JOIN experiences e
  ON e.company_id = c.id AND e.status = 'published'
GROUP BY c.id, c.slug;

CREATE UNIQUE INDEX ON company_stats (company_id);
```

Create equivalents:

- `location_stats` — `GROUP BY location_id`
- `company_role_stats` — `GROUP BY (company_id, role_id)`
- `company_location_stats` — `GROUP BY (company_id, location_id)`

Refresh on a schedule (e.g. every 5 minutes via a Spring `@Scheduled`
job calling `REFRESH MATERIALIZED VIEW CONCURRENTLY ...`), and/or after
every successful insert if low write volume.

### Option B — Compute on the fly

For low-traffic launches, `JOIN ... GROUP BY` at query time is fine.
Promote to materialized views once read latency becomes a concern.

---

## 4. HTTP API surface

URL conventions: `/api/v1/...`, JSON in/out, cursor pagination
(`?cursor=...&limit=...`). All endpoints are **public** in v1.
Mutating endpoints are rate-limited by IP (see §6).

### 4.1 Companies

| Method | Path                            | Purpose                                          |
|--------|---------------------------------|--------------------------------------------------|
| GET    | `/api/v1/companies`             | List companies (search, sort, filter, paginate)  |
| GET    | `/api/v1/companies/{slug}`      | Company detail + aggregate stats                 |
| GET    | `/api/v1/companies/{slug}/roles`| Roles available at this company + per-role stats |

**Query params for `GET /api/v1/companies`:**

- `q` — substring match on `name` (case-insensitive)
- `industry` — exact match
- `sort` — one of `worth_score`, `experience_count`, `name` (default `name`)
- `order` — `asc` | `desc` (default `asc`)
- `cursor` — opaque base64 cursor
- `limit` — 1..100 (default 20)

**Example response — `GET /api/v1/companies?q=amaz&sort=worth_score&order=desc&limit=20`:**

```json
{
  "items": [
    {
      "id": 1,
      "slug": "amazon",
      "name": "Amazon",
      "industry": "Tech",
      "headquarters": "Seattle, WA",
      "stats": {
        "experience_count": 12,
        "avg_worth_score": 7.2,
        "avg_base_salary": 165000,
        "avg_stress": 7.0
      }
    }
  ],
  "next_cursor": "eyJpZCI6MjB9"
}
```

### 4.2 Roles & Experiences

| Method | Path                                                       | Purpose                                          |
|--------|------------------------------------------------------------|--------------------------------------------------|
| GET    | `/api/v1/roles`                                            | Global role list                                 |
| GET    | `/api/v1/companies/{slug}/roles/{roleSlug}`                | Role aggregates at this company                  |
| GET    | `/api/v1/companies/{slug}/roles/{roleSlug}/experiences`    | Paginated experiences (optional `?city=` filter) |
| GET    | `/api/v1/experiences/{id}`                                 | Single experience                                |
| POST   | `/api/v1/experiences`                                      | Submit a new (anonymous) experience              |

**Request body — `POST /api/v1/experiences`:**

```json
{
  "company_slug": "amazon",
  "role_slug": "software-engineer",
  "custom_role": null,
  "level_name": "SDE II",
  "city": "Seattle",
  "state": "WA",
  "employment_status": "current",
  "years_experience": 3,
  "years_at_company": 2,
  "base_salary": 145000,
  "bonus": 15000,
  "stock": 20000,
  "signing_bonus": 10000,
  "compensation_year": 2026,
  "stress_level": 6.5,
  "hours_per_week": 45,
  "worth_it_score": 7.5,
  "wish_knew": "On-call was heavier than expected."
}
```

Returns `201 Created` with the full experience body, including the
generated `id`, `created_at`, and any server-resolved FKs.

**Error responses:** `400 Bad Request` for validation failure (return a
JSON body listing field errors), `404 Not Found` for unknown
slug/`{id}`, `409 Conflict` for duplicate slug on lookup creation,
`429 Too Many Requests` when rate-limited (with `Retry-After` header).

### 4.3 Locations

| Method | Path                                  | Purpose                                                  |
|--------|---------------------------------------|----------------------------------------------------------|
| GET    | `/api/v1/locations`                   | List cities + aggregate stats                            |
| GET    | `/api/v1/locations/{slug}`            | City detail + aggregate stats                            |
| GET    | `/api/v1/locations/{slug}/companies`  | Companies with experiences in this city + per-city stats |

### 4.4 Search

| Method | Path                   | Purpose                                             |
|--------|------------------------|-----------------------------------------------------|
| GET    | `/api/v1/search?q=...` | Federated search across companies, roles, locations |

```json
{
  "companies": [{ "slug": "amazon", "name": "Amazon" }],
  "roles":     [{ "slug": "software-engineer", "name": "Software Engineer" }],
  "locations": [{ "slug": "seattle", "display_name": "Seattle, WA" }]
}
```

### 4.5 Health

| Method | Path                  | Purpose                                |
|--------|-----------------------|----------------------------------------|
| GET    | `/actuator/health`    | Liveness/readiness probe (public)      |

All other `/actuator/*` endpoints **must** be disabled or secured.

---

## 5. Validation rules

Apply server-side via Jakarta Bean Validation (`@Valid`, `@NotNull`,
`@Min`, `@Max`, `@Size`, `@Pattern`). Mirror the DB `CHECK`
constraints:

- `years_experience`, `years_at_company`: 0–60
- `base_salary`, `bonus`, `stock`, `signing_bonus`: ≥ 0 (whole dollars)
- `compensation_year`: 2000–2100
- `stress_level`, `worth_it_score`: 0.0–10.0
- `hours_per_week`: 0–168
- `employment_status`: `current` | `past`
- `state`: exactly 2 uppercase letters
- `wish_knew`: max 2000 chars
- `custom_role`: max 100 chars
- Unknown JSON fields: rejected (`FAIL_ON_UNKNOWN_PROPERTIES = true`)
- Request body size: capped at 64 KB

Slug rules (used to generate `slug` columns at insert time):

1. Lowercase.
2. Replace runs of non-alphanumeric characters with a single `-`.
3. Trim leading/trailing `-`.
4. Truncate to 80 chars.

Never recompute slugs at read time; store at insert.

---

## 6. Security posture

The API is public (no auth) but only the WorthIt frontend is expected
to call it. Apply defense in depth:

1. **Edge / CDN** (Cloudflare, AWS CloudFront + WAF, or Fastly):
   DDoS protection, OWASP Core Rule Set, geo / bot filtering, and coarse
   rate limits (e.g. 100 req/min/IP across the API). Lock the origin's
   security group to CDN IP ranges so the JVM is never directly
   reachable.
2. **Application rate limits** (Spring Boot + Bucket4j, backed by
   Redis for multi-instance correctness):
    - `POST /api/v1/experiences`: 5/day/IP and 1/min/IP
    - `GET /api/v1/search`: 30/min/IP
    - Other GETs: 120/min/IP
    - Return `429` with `Retry-After`.
3. **CORS** (`spring-web` `CorsConfiguration`): allow only the
   production frontend origin(s); `allowedMethods = [GET, POST]`;
   `allowCredentials = false`.
4. **Bot / abuse protection on writes:** require a Cloudflare Turnstile
   (or hCaptcha / reCAPTCHA v3) token in the submit request; verify
   server-side before persisting. Add a hidden honeypot field and reject
   submissions whose token age is < 3 seconds.
5. **Input validation + payload caps:** see §5. Sanitize free-text
   fields (`wish_knew`, `custom_role`) with OWASP Java HTML Sanitizer
   before storing to prevent stored XSS when re-rendered.
6. **Spring Security filter chain** (no auth, but hardened):
   ```java
   http
     .csrf(csrf -> csrf.disable())
     .cors(Customizer.withDefaults())
     .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
     .headers(h -> h
       .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'"))
       .frameOptions(f -> f.deny())
       .httpStrictTransportSecurity(Customizer.withDefaults()))
     .authorizeHttpRequests(a -> a
       .requestMatchers("/actuator/health").permitAll()
       .requestMatchers("/actuator/**").denyAll()
       .anyRequest().permitAll());
   ```
7. **SQL safety:** Spring Data JPA / JDBC with parameterized queries
   only. Never interpolate user input into SQL — including the search
   `ILIKE` / `pg_trgm` queries; always bind as parameters.
8. **Database hardening:**
    - Application DB user has `SELECT/INSERT/UPDATE` on app tables only;
      no `DROP`, no superuser.
    - `SET statement_timeout = '3s'` on the role.
    - HikariCP `maximum-pool-size` sized for the DB (e.g. 10–20).
9. **Operational hygiene:** HTTPS only with HSTS; TLS 1.2+; secrets via
   env vars / AWS Secrets Manager (never in code or `application.yml`);
   keep Spring Boot patched; daily `pg_dump` backups; a Redis-backed
   config flag acting as a kill-switch for `POST /api/v1/experiences`.

`application.yml` size caps:

```yaml
spring:
  servlet:
    multipart:
      max-request-size: 64KB
  codec:
    max-in-memory-size: 64KB
server:
  tomcat:
    max-http-form-post-size: 64KB
```

---

## 7. Observability

- **Logging:** Log every `POST` with request id, IP hash, user-agent,
  resolved company/role/location ids, latency, and response status.
  Never log full PII or raw free-text payloads at INFO level.
- **Metrics:** Micrometer → Prometheus. Expose request counts and
  latency per endpoint, 4xx/5xx rates, DB pool usage, rate-limit
  rejections, and submission counts.
- **Alerts:** spike in 4xx, spike in 5xx, spike in submissions/min from
  a single IP or ASN, DB connection pool saturation.

---

## 8. Seed data

Seed the lookup tables before opening writes:

- **`companies`** (~20): Amazon, Google, Meta, Microsoft, Stripe,
  Netflix, Apple, Uber, Airbnb, Salesforce, Tesla, LinkedIn, Coinbase,
  Snap, Lyft, DoorDash, Robinhood, Adobe, Dropbox, Instacart.
- **`roles`** (~4): Software Engineer, Senior Software Engineer,
  Staff Engineer, Engineering Manager.
- **`locations`** (~13): Seattle WA, San Francisco CA, Austin TX, New
  York NY, Mountain View CA, Menlo Park CA, Redmond WA, Los Gatos CA,
  Los Angeles CA, Cupertino CA, Sunnyvale CA, Palo Alto CA, San Jose CA.
- **`levels`**: per-company distinct `(company, level_name,
  normalized_rank)` rows for the ladders in §2.4.

Use Flyway or Liquibase migrations under `src/main/resources/db/migration/`
to version both schema and seed data.

After seeding, refresh materialized views.

---

## 9. Future additions (when auth ships)

Layer these in **additively** — none requires changing the v1 tables
above, only adding new ones plus a nullable `user_id` on `experiences`:

- `users` table (id, email, display_name, auth_provider, is_verified, is_admin).
- `experiences.user_id BIGINT NULL REFERENCES users(id)` — nullable so
  v1 anonymous rows remain valid.
- `experience_votes` (helpful / not-helpful, one per user per experience).
- `experience_reports` (spam / inappropriate flagging).
- `saved_companies` (favorites per user).
- `submission_drafts` (server-side autosave of in-progress submissions).
- `/api/v1/auth/*` endpoints (signup, login, magic-link, me, OAuth).
- `/api/v1/me/*` endpoints (saved-companies, drafts).
- `/api/v1/experiences/{id}/{vote,report}` endpoints.
- `/api/v1/admin/*` moderation endpoints (locked to `is_admin`).

When auth ships, **never** return `user_id` from public endpoints.

---

## 10. Implementation notes

- **Stack:** Spring Boot 3.x, Java 21, Spring Web, Spring Data JPA,
  Spring Security, Spring Validation, Flyway (or Liquibase), Bucket4j,
  Micrometer, HikariCP, PostgreSQL JDBC driver.
- **Soft delete:** prefer `status = 'removed'` over `DELETE` so flagged
  content remains investigatable.
- **Search:** start with Postgres `ILIKE` / `pg_trgm` indexes on
  `companies.name`, `roles.name`, `locations.city`. Move to `tsvector`
  or an external search engine (Meilisearch / OpenSearch) once the
  dataset grows.
- **Pagination:** cursor = base64-encoded JSON of the last item's sort
  key + id; never offset-paginate large lists.
- **Time zone:** server runs in UTC; clients format dates locally.
- **Backups:** daily `pg_dump`, retained 30 days minimum.

---

## 11. Build order

1. Provision PostgreSQL; create the app role with minimal privileges.
2. Add Flyway migrations for the 5 tables in §2 and the materialized
   views in §3.
3. Seed lookups from §8.
4. Implement endpoints in this order:
   `companies` (list + detail) → `experiences` (POST + list) →
   `locations` → `search`.
5. Wire Spring Security (§6), rate limiting, CORS, and Turnstile
   verification.
6. Add Micrometer metrics, structured logging, and alerts (§7).
7. Put the service behind a CDN/WAF and lock the origin (§6.1).
