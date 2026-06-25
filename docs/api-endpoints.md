# WorthIt — Backend API Endpoints

This document specifies the HTTP endpoints the WorthIt backend needs to expose
so the UI can stop using hardcoded data.

Companion docs:
- [`database-spec.md`](./database-spec.md) — tables/columns these endpoints read/write.
- [`worthit/docs/ui-endpoint-usage.md`](../../worthit/docs/ui-endpoint-usage.md) — where each endpoint is consumed in the UI.

> The UI's API layer already encodes this contract in code:
> `worthit/src/lib/apiClient.js`, `worthit/src/features/companies/api/companyApi.js`,
> and `worthit/src/features/locations/api/locationApi.js`. The paths and shapes
> below match what those files already call, so implementing them makes the UI
> work without UI changes (except for a few field-name notes called out below).

---

## 1. Conventions

- **Base path:** all endpoints are under `/api/v1`.
- **Base URL (dev):** `http://localhost:8080` (UI reads `VITE_API_BASE_URL`).
- **Content type:** `application/json` for requests and responses.
- **Auth:** read endpoints are public; the submit endpoint may require a JWT
  (see §7). Security is wired but currently disabled
  (`security.auth.enabled=false`).
- **Errors:** every error returns the shared envelope produced by
  `GlobalExceptionHandler` / `ApiErrorResponse`:

```json
{
  "timestamp": "2026-06-20T15:06:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Requested resource was not found",
  "path": "/api/v1/companies/does-not-exist",
  "details": null
}
```

The UI's `apiClient.js` already throws `ApiException` wrapping this shape.

### Pagination (cursor-based)

List endpoints return a `Page<T>` envelope and accept `cursor` + `limit`:

```json
{
  "items": [ /* ...T... */ ],
  "next_cursor": "opaque-string-or-null"
}
```

- `limit` — page size (default e.g. 20, max e.g. 50).
- `cursor` — opaque token from the previous response's `next_cursor`; omit for
  the first page. `next_cursor` is `null` on the last page.

> The UI typeahead hook reads `page.items` (falling back to `page.data`), so
> `items` is the required key.

### Slugs

Path params `{slug}` / `{roleSlug}` / `{citySlug}` use the slug rules in
`database-spec.md` §1. They must match the UI's `slug()` output exactly.

---

## 2. Companies

### 2.1 List / search companies ✅
`GET /api/v1/companies`

Query params (all optional):

| Param      | Type   | Description                                            |
|------------|--------|--------------------------------------------------------|
| `q`        | string | Case-insensitive substring match on company name.      |
| `industry` | string | Filter by industry (e.g. `Tech`).                      |
| `sort`     | string | Sort field (e.g. `name`, `worthScore`, `experiences`). |
| `order`    | string | `asc` or `desc`.                                       |
| `cursor`   | string | Pagination cursor.                                     |
| `limit`    | int    | Page size.                                             |

Returns `Page<CompanySummary>`:

```json
{
  "items": [
    {
      "slug": "amazon",
      "name": "Amazon",
      "industry": "Tech",
      "headquarters": "Seattle, WA",
      "experienceCount": 5,
      "roleCount": 3,
      "avgWorthScore": 7.4,
      "avgStress": 6.8
    }
  ],
  "next_cursor": null
}
```

Used by: companies list page, homepage company typeahead/search.

### 2.2 Company detail ✅
`GET /api/v1/companies/{slug}`

Returns `CompanyDetail` — the company's basic profile only (intentionally lightweight, no
aggregate stats):

```json
{
  "slug": "amazon",
  "name": "Amazon",
  "industry": "Tech",
  "headquarters": "Seattle, WA"
}
```

`404` if the slug does not exist.

### 2.3 Company roles ✅
`GET /api/v1/companies/{slug}/roles`

Query params (all optional):

| Param    | Type   | Description          |
|----------|--------|----------------------|
| `cursor` | string | Pagination cursor.   |
| `limit`  | int    | Page size.           |

Returns `Page<RoleSummary>` — the roles available at the company (driven by the
`company_role` join, see `database-spec.md` §5), each with per-role aggregate
stats computed from the company's `published` experiences. Salary figures are
whole USD, where `baseSalaryAverage` is the mean of the role's base salaries
(sum ÷ count); a role with no published experiences yet is still listed with
`null` stats and `experienceCount: 0`. `404` if the slug does not exist.

```json
{
  "items": [
    {
      "slug": "software-engineer",
      "name": "Software Engineer",
      "experienceCount": 3,
      "avgWorthScore": 7.5,
      "avgStress": 6.5,
      "baseSalaryMin": 130000,
      "baseSalaryMax": 145000,
      "baseSalaryAverage": 138333
    }
  ],
  "next_cursor": null
}
```

Used by: company detail page (role cards).

### 2.4 Experiences for a company + role ✅
`GET /api/v1/companies/{slug}/roles/{roleSlug}/experiences`

Query params (all optional):

| Param    | Type   | Description                                  |
|----------|--------|----------------------------------------------|
| `city`   | string | City **slug** filter (e.g. `seattle-wa`).    |
| `cursor` | string | Pagination cursor.                           |
| `limit`  | int    | Page size (default `20`, max `50`).          |

Returns `Page<ExperienceSummary>`, newest first (`created_at` desc, `id` desc
tiebreaker), restricted to `published` experiences. Each item **mirrors the
`experience` DB columns** (see `database-spec.md` §8) rather than the UI's mock
field names: field keys come from the global `snake_case` Jackson strategy
(e.g. `worth_it_score`, `stress_level`, `wish_knew`, `created_at`), and the
foreign keys are expanded into their natural identifiers (company/role slug +
name, location slug/city/state, level name). `id` is the numeric experience id.

`employment_status` is the DB enum value — one of `current` / `past` (note:
`past`, not `former`). These fields are nullable and serialize as `null` when
unset: `level_name`, `years_at_company`, `hours_per_week`, `why_stay`,
`why_leave`, `wish_knew`. `created_at` is an ISO-8601 timestamp (UTC).

```json
{
  "items": [
    {
      "id": 1,
      "company_slug": "amazon",
      "company_name": "Amazon",
      "role_slug": "software-engineer",
      "role_name": "Software Engineer",
      "location_slug": "seattle-wa",
      "city": "Seattle",
      "state": "WA",
      "level_name": "SDE II",
      "employment_status": "current",
      "years_experience": 3,
      "years_at_company": 2,
      "base_salary": 145000,
      "bonus": 15000,
      "stock": 20000,
      "signing_bonus": 10000,
      "compensation_year": 2025,
      "stress_level": 6.5,
      "hours_per_week": 45,
      "worth_it_score": 7.5,
      "why_stay": "Strong comp and learning.",
      "why_leave": "On-call burnout.",
      "wish_knew": "Ask about on-call rotation before joining.",
      "created_at": "2026-05-01T12:00:00Z"
    }
  ],
  "next_cursor": null
}
```

> The role list / role slug comes from §2.3. `404` if the company slug or role
> slug does not exist, or if the role is not offered at the company (no
> `company_role` link); an unknown `city` slug yields an empty page (not a 404).

Used by: experiences list page + individual experience modal.

### 2.5 Company search (typeahead) ✅
`GET /api/v1/companies/search`

A deliberately lightweight, search-bar-only endpoint. Given the substring a user
is typing (`am` → `ama` → `amaz`), it returns the matching companies' **basic
profiles only** — no aggregate stats, no sort knobs. Use §2.1 for the full
companies list page; use this for autocomplete/typeahead where latency matters.

Query params:

| Param   | Type   | Description                                                       |
|---------|--------|-------------------------------------------------------------------|
| `q`     | string | **Required.** Case-insensitive substring match on company name.   |
| `limit` | int    | Optional max results (default e.g. 8, max e.g. 20).               |

Returns `Page<CompanyDetail>`, where each item is the lightweight `CompanyDetail`
shape from §2.2 (`slug`, `name`, `industry`, `headquarters`). Results are name-
sorted; `next_cursor` is `null` (typeahead returns a single capped page):

```json
{
  "items": [
    {
      "slug": "amazon",
      "name": "Amazon",
      "industry": "Tech",
      "headquarters": "Seattle, WA"
    }
  ],
  "next_cursor": null
}
```

> A blank/missing `q` returns an empty `items` list (the search bar has nothing
> to match yet).

Used by: homepage / global search bar company typeahead.

---

## 3. Locations

### 3.1 List / search locations ✅
`GET /api/v1/locations` 

Query params: `q` (city name substring), `cursor`, `limit`.

Returns `Page<LocationSummary>`:

```json
{
  "items": [
    {
      "slug": "seattle-wa",
      "city": "Seattle",
      "state": "WA",
      "experienceCount": 12,
      "companyCount": 6,
      "avgWorthScore": 7.6,
      "avgStress": 6.4
    }
  ],
  "next_cursor": null
}
```

### 3.2 Location detail ✅
`GET /api/v1/locations/{slug}`

Returns a single `LocationSummary` (same shape as above). `404` if not found.

### 3.3 Companies in a location ✅
`GET /api/v1/locations/{slug}/companies`

Returns the companies that have experiences in this city, with per-company
stats scoped to the city:

```json
{
  "items": [
    {
      "slug": "amazon",
      "name": "Amazon",
      "industry": "Tech",
      "experienceCount": 4,
      "avgWorthScore": 7.5,
      "avgStress": 6.6
    }
  ],
  "next_cursor": null
}
```

Used by: location detail page.

---

## 4. Submit an experience (write path)

### 4.1 Create experience
`POST /api/v1/experiences`

Creates a new experience. The UI's multi-step form
(`worthit/src/components/SubmitExperience.jsx`) currently only `console.log`s
its `formData`; this endpoint is its target.

**Request body** (maps the form's `formData` to DB columns; numbers are whole
USD, scores 0.0–10.0):

```json
{
  "companySlug": "amazon",
  "company": "Amazon",
  "roleSlug": "software-engineer",
  "role": "Software Engineer",
  "customRole": null,
  "level": "SDE II",
  "employmentStatus": "current",
  "city": "Seattle",
  "state": "WA",
  "yearsExperience": 3,
  "yearsAtCompany": 2,
  "baseSalary": 145000,
  "bonus": 15000,
  "stock": 20000,
  "signingBonus": 10000,
  "compensationYear": 2025,
  "stressLevel": 6.5,
  "hoursPerWeek": 45,
  "worthItScore": 7.5,
  "whyStay": "Strong comp and learning.",
  "whyLeave": "On-call burnout.",
  "wishKnew": "Ask about on-call rotation before joining."
}
```

Mapping notes:
- `company`/`role` may be sent as either an existing slug or a display name; if
  the company/role is new, the backend can create it (or reject — decide
  moderation policy). `customRole` is used when the user typed a role not in the
  list.
- The form's culture sliders (`autonomy`, `coding`, `meetings`,
  `firefighting`, `micromanagement`, `psychologicalSafety`, `feedbackQuality`,
  `growthOpportunities`, `followManager`, `reviews`) are collected by the UI but
  not yet displayed; accept and ignore, or persist per `database-spec.md` §8
  ("culture sub-scores").

**Validation** (Bean Validation; errors surface via `GlobalExceptionHandler`):
- `companySlug`/`company`, `role`/`customRole`, `city` required.
- `baseSalary` required, `>= 0`; `bonus`/`stock`/`signingBonus` `>= 0`.
- `stressLevel`, `worthItScore` within `0.0–10.0`.
- `employmentStatus` ∈ {`current`, `former`}.

**Response:** `201 Created` with the created `Experience` (same shape as §2.4),
or `400` with validation `details`.

New experiences are created with `status = pending` (see `database-spec.md` §9)
and therefore do **not** appear in read endpoints until published/moderated.

---

## 5. Reference / lookup endpoints (supporting the submit form)

These help the submit form populate dropdowns. Optional for a first pass (the
form can use free text), but recommended:

| Endpoint                                   | Purpose                                  |
|--------------------------------------------|------------------------------------------|
| `GET /api/v1/companies/search?q=...&limit=8` | Company typeahead (lightweight, §2.5).   |
| `GET /api/v1/roles`                         | Global list of roles for the role picker.|
| `GET /api/v1/companies/{slug}/levels`       | Per-company level options for the form.  |

`GET /api/v1/roles` returns `Page<RoleSummary>` (`slug`, `name`, `family`).
`GET /api/v1/companies/{slug}/levels` returns levels ordered by
`normalizedRank` (`name`, `normalizedRank`).

---

## 6. Health / hello

Already implemented by `HelloController` (starter). Keep for readiness checks:

- `GET /hello` (or `/api/hello`) → simple liveness string.

---

## 7. Authentication & security notes

- Security is wired via `spring-boot-starter-oauth2-resource-server` but
  **disabled** by default (`security.auth.enabled=false`,
  `SecurityConfigLocal`). All endpoints are open while disabled.
- When auth is enabled (`security.auth.enabled=true`), configure
  `security.jwt.issuer`, `security.jwt.jwks-uri`, `security.jwt.expected-audience`
  (Supabase/Auth0/Cognito — TBD) in `application.properties`.
- Suggested policy once auth is on:
  - **Public (no token):** all `GET` read endpoints in §2, §3, §5, §6.
  - **Authenticated (valid JWT):** `POST /api/v1/experiences` (§4), so
    submissions are attributable / rate-limitable. Until then it can stay public.
- CORS: allowed origins come from `app.security.cors.allowed-origins`
  (currently `http://localhost:3000,http://localhost:5173`). Add the deployed UI
  origin per environment.
- Unauthorized/forbidden requests return the standard error envelope with
  status `401` / `403` (handled in `GlobalExceptionHandler`).

---

## 8. Endpoint summary

| Method | Path                                                        | Auth (when on) | UI consumer                       |
|--------|-------------------------------------------------------------|----------------|-----------------------------------|
| GET    | `/api/v1/companies`                                         | public         | companies list, home search       |
| GET    | `/api/v1/companies/{slug}`                                  | public         | company detail                    |
| GET    | `/api/v1/companies/search`                                  | public         | search bar typeahead              |
| GET    | `/api/v1/companies/{slug}/roles`                            | public         | company detail (roles)            |
| GET    | `/api/v1/companies/{slug}/roles/{roleSlug}/experiences`     | public         | experiences list + modal          |
| GET    | `/api/v1/locations`                                         | public         | locations list                    |
| GET    | `/api/v1/locations/{slug}`                                  | public         | location detail                   |
| GET    | `/api/v1/locations/{slug}/companies`                        | public         | location detail (companies)       |
| GET    | `/api/v1/roles`                                             | public         | submit form (role picker)         |
| GET    | `/api/v1/companies/{slug}/levels`                           | public         | submit form (level picker)        |
| POST   | `/api/v1/experiences`                                       | authenticated  | submit experience form            |
| GET    | `/hello`                                                    | public         | health check                      |
