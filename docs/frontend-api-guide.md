# WorthIt Backend — Frontend API Guide

This document is the **frontend integration contract** for the WorthIt backend.
It describes every HTTP endpoint the React UI can call: URL, method, query
params, request body, response shape, status codes, error envelope, and TypeScript
types you can paste directly into the UI.

> **Spec source of truth:** [`backend_spec.md`](./backend_spec.md). This guide
> reflects what is actually implemented today; if the two diverge, the spec is
> authoritative for design intent and this guide is authoritative for behavior.

---

## 1. Conventions

### 1.1 Base URL

All domain endpoints live under `/api/v1/...`. There is one legacy starter
endpoint at `/api/hello` and the actuator at `/actuator/health`.

| Environment | Base URL                       |
|-------------|--------------------------------|
| Local dev   | `http://localhost:8080`        |
| Production  | TBD (set via env var in UI)    |

Recommended UI setup:

```ts
// e.g. .env.local
VITE_API_BASE_URL=http://localhost:8080
```

### 1.2 Content type

- All endpoints accept and return `application/json; charset=utf-8`.
- The UI should always send `Content-Type: application/json` and
  `Accept: application/json` on requests with a body.

### 1.3 JSON casing

The backend uses **`snake_case`** for every JSON property, both in requests
and responses. Examples: `worth_it_score`, `base_salary`, `next_cursor`,
`company_slug`.

If you prefer `camelCase` in TypeScript, convert at the boundary (e.g. with
a tiny `snakecase-keys` / `camelcase-keys` wrapper around `fetch`).

### 1.4 Authentication

**v1 is fully public — no authentication required.** Do **not** send an
`Authorization` header. There are no cookies, no sessions, no CSRF token.

This will change when auth ships (see spec §9). Until then:

- CORS is enabled for the production frontend origin(s).
- `allowCredentials = false`. Don't set `credentials: 'include'` on `fetch`.

### 1.5 Time & money

- All timestamps are ISO-8601 UTC, e.g. `"2026-06-11T20:41:00Z"`.
- All money values are **whole US dollars** as integers (no cents).
  `base_salary: 145000` means `$145,000`.
- `stress_level` and `worth_it_score` are numbers in `[0.0, 10.0]` with
  one decimal place.

### 1.6 Cursor pagination

List endpoints use **opaque cursor pagination**, not offset/limit pages.

Query params on every paginated endpoint:

| Param    | Type   | Default | Description                                         |
|----------|--------|---------|-----------------------------------------------------|
| `cursor` | string | `null`  | Opaque cursor from a previous response. Omit on the first call. |
| `limit`  | int    | `20`    | Page size, clamped to `[1, 100]`.                   |

Response shape:

```json
{
  "items": [ /* ... */ ],
  "next_cursor": "eyJrIjoiYW1hem9uIiwiaWQiOjF9"
}
```

- If `next_cursor` is **present**, there is more data — pass it back as
  `?cursor=...` on the next call.
- If `next_cursor` is **absent / `null`**, you have reached the end.
- Never parse, decode, or generate cursors on the client. They are opaque
  base64 JSON managed by the server.

TypeScript:

```ts
export interface Page<T> {
  items: T[];
  next_cursor?: string | null;
}
```

### 1.7 Error envelope

Every non-2xx response (except `404` for completely unmapped routes) returns
this JSON body:

```json
{
  "timestamp": "2026-06-11T20:41:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/experiences",
  "details": [
    "baseSalary: must be greater than or equal to 0",
    "state: must be 2 uppercase letters"
  ]
}
```

TypeScript:

```ts
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[]; // field-level errors when status === 400
}
```

Status codes you can expect:

| Status | Meaning                                                      |
|--------|--------------------------------------------------------------|
| `200`  | Success (GET, or POST returning current state).              |
| `201`  | Created — only `POST /api/v1/experiences`.                   |
| `400`  | Validation failure. Look at `details[]` to highlight fields. |
| `404`  | Slug / id not found (e.g. unknown company slug).             |
| `429`  | Rate-limited (spec §6). Will arrive with `Retry-After` header — not yet enforced in v1. |
| `500`  | Unexpected server error. Show a generic "try again" toast.   |

---

## 2. TypeScript model types

Paste these into the UI (e.g. `src/api/types.ts`). They mirror the backend DTOs
exactly. All numeric stats may be `null` when there are no experiences yet.

```ts
export interface CompanyStats {
  experience_count: number;
  avg_worth_score?: number | null;   // 0.0–10.0
  avg_base_salary?: number | null;   // whole USD
  avg_bonus?: number | null;
  avg_stock?: number | null;
  avg_stress?: number | null;        // 0.0–10.0
  avg_hours?: number | null;
}

export interface Company {
  id: number;
  slug: string;
  name: string;
  industry?: string | null;
  headquarters?: string | null;
  website_url?: string | null;
  description?: string | null;
  stats: CompanyStats;
}

export interface RoleStats {
  experience_count: number;
  avg_worth_score?: number | null;
  avg_base_salary?: number | null;
  avg_stress?: number | null;
}

export interface Role {
  id: number;
  slug: string;
  name: string;
  family?: string | null;
}

export interface CompanyRole extends Role {
  stats: RoleStats;
}

export interface LocationStats {
  experience_count: number;
  avg_worth_score?: number | null;
  avg_base_salary?: number | null;
  avg_stress?: number | null;
  avg_hours?: number | null;
}

export interface Location {
  id: number;
  slug: string;
  city: string;
  state: string;          // 2 uppercase letters, e.g. "WA"
  display_name: string;   // "Seattle, WA"
  stats: LocationStats;
}

export type EmploymentStatus = 'current' | 'past';
export type ExperienceStatus = 'pending' | 'published' | 'removed';

export interface Experience {
  id: number;
  company_slug: string;
  company_name: string;
  role_slug: string;
  role_name: string;
  custom_role?: string | null;
  level_id?: number | null;
  level_name?: string | null;
  city: string;
  state: string;
  display_location: string;
  employment_status: EmploymentStatus;
  years_experience: number;
  years_at_company?: number | null;
  base_salary: number;
  bonus: number;
  stock: number;
  signing_bonus: number;
  total_comp: number;          // derived: base + bonus + stock + signing
  compensation_year: number;
  stress_level: number;        // 0.0–10.0
  hours_per_week: number;
  worth_it_score: number;      // 0.0–10.0
  wish_knew?: string | null;
  extras?: Record<string, unknown> | null;
  status: ExperienceStatus;
  created_at: string;          // ISO-8601 UTC
}

export interface CreateExperienceRequest {
  company_slug: string;        // must exist (404 if not)
  role_slug: string;           // must exist (404 if not)
  custom_role?: string | null; // max 100 chars
  level_name?: string | null;  // free text; max 100 chars
  city: string;                // auto-creates location if (city,state) is new
  state: string;               // exactly 2 uppercase letters
  employment_status: EmploymentStatus;
  years_experience: number;    // 0..60
  years_at_company?: number | null; // 0..60
  base_salary: number;         // whole USD, >= 0
  bonus?: number;              // default 0
  stock?: number;
  signing_bonus?: number;
  compensation_year: number;   // 2000..2100
  stress_level: number;        // 0.0–10.0
  hours_per_week: number;      // 0..168
  worth_it_score: number;      // 0.0–10.0
  wish_knew?: string | null;   // max 2000 chars
}

export interface SearchResponse {
  companies: Array<{ slug: string; name: string }>;
  roles:     Array<{ slug: string; name: string }>;
  locations: Array<{ slug: string; display_name: string }>;
}
```

---

## 3. Endpoint reference

### 3.1 Health

#### `GET /actuator/health`

Liveness/readiness probe. Public.

```http
GET /actuator/health
```

```json
{ "status": "UP" }
```

Use this for connectivity checks; do not rely on it for any business logic.

#### `GET /api/hello`

Legacy smoke-test endpoint. Returns a tiny JSON payload. The UI normally
doesn't need this; it exists for backend devs.

```json
{ "app": "worthit-backend", "status": "ok", "message": "Hello from WorthIt backend" }
```

---

### 3.2 Companies

#### `GET /api/v1/companies` — list companies

Query params:

| Param      | Type   | Default | Notes                                                |
|------------|--------|---------|------------------------------------------------------|
| `q`        | string | —       | Case-insensitive substring match on `name`.          |
| `industry` | string | —       | Exact match (e.g. `Tech`, `Fintech`).                |
| `sort`     | string | `name`  | One of `name` \| `worth_score` \| `experience_count`. |
| `order`    | string | `asc`   | `asc` \| `desc`.                                     |
| `cursor`   | string | —       | See §1.6.                                            |
| `limit`    | int    | `20`    | 1–100.                                               |

Example:

```http
GET /api/v1/companies?q=amaz&sort=worth_score&order=desc&limit=20
```

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
        "avg_bonus": 18000,
        "avg_stock": 60000,
        "avg_stress": 7.0,
        "avg_hours": 47.5
      }
    }
  ],
  "next_cursor": "eyJpZCI6MX0"
}
```

Response type: `Page<Company>`.

#### `GET /api/v1/companies/{slug}` — company detail

```http
GET /api/v1/companies/amazon
```

Returns a single `Company` with `stats`. `404` if the slug doesn't exist.

#### `GET /api/v1/companies/{slug}/roles` — roles at this company

Returns every role that has at least one published experience at this
company, with per-role aggregate stats.

```http
GET /api/v1/companies/amazon/roles
```

Response type: `CompanyRole[]`. Returns `[]` (empty array) — not 404 — if
the company exists but has no experiences yet. `404` only if the company
slug itself is unknown.

#### `GET /api/v1/companies/{slug}/roles/{roleSlug}` — role-at-company

Aggregate stats for one `(company, role)` pair.

```http
GET /api/v1/companies/amazon/roles/software-engineer
```

Response type: `CompanyRole`. `404` if either slug is unknown. `stats` will
have `experience_count = 0` and null averages if the pair has no
experiences yet.

#### `GET /api/v1/companies/{slug}/roles/{roleSlug}/experiences` — experience list

Cursor-paginated list of published experiences for a `(company, role)`.

Query params:

| Param    | Type   | Notes                                                       |
|----------|--------|-------------------------------------------------------------|
| `city`   | string | Optional location slug filter (e.g. `seattle-wa`).          |
| `cursor` | string | See §1.6.                                                   |
| `limit`  | int    | 1–100 (default 20).                                         |

```http
GET /api/v1/companies/amazon/roles/software-engineer/experiences?city=seattle-wa&limit=20
```

Response type: `Page<Experience>`. Sorted by newest first.

---

### 3.3 Roles

#### `GET /api/v1/roles` — global role list

```http
GET /api/v1/roles
```

```json
[
  { "id": 1, "slug": "software-engineer", "name": "Software Engineer", "family": "Engineering" },
  { "id": 2, "slug": "senior-software-engineer", "name": "Senior Software Engineer", "family": "Engineering" },
  { "id": 3, "slug": "staff-engineer", "name": "Staff Engineer", "family": "Engineering" },
  { "id": 4, "slug": "engineering-manager", "name": "Engineering Manager", "family": "Engineering" }
]
```

Response type: `Role[]`. Not paginated — there are only a handful of roles.

---

### 3.4 Experiences

#### `POST /api/v1/experiences` — submit a new experience

Anonymous. Validates the body against the rules in `CreateExperienceRequest`.
Companies and roles must already exist (404 if not). The `(city, state)`
location is auto-created if missing.

Request:

```http
POST /api/v1/experiences
Content-Type: application/json
```

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

Response: `201 Created` with the full `Experience` (including server-assigned
`id`, `created_at`, resolved `total_comp`, etc.).

Notable error cases:

- `400 Bad Request` — any validation failure. `details[]` lists field-level
  messages. **Frontend should map `details[]` entries back onto form fields**
  by splitting on the first `:` (the prefix is the field name, e.g.
  `baseSalary: must be greater than or equal to 0`).
- `404 Not Found` — unknown `company_slug` or `role_slug`.

Unknown JSON fields are **rejected** (`FAIL_ON_UNKNOWN_PROPERTIES = true`).
Make sure you only send the documented keys.

#### `GET /api/v1/experiences/{id}` — single experience

```http
GET /api/v1/experiences/123
```

Returns a single `Experience`. `404` if the id is unknown or the experience
has been soft-removed (`status = 'removed'`).

---

### 3.5 Locations

#### `GET /api/v1/locations` — list locations

Query params:

| Param    | Type   | Notes                                                  |
|----------|--------|--------------------------------------------------------|
| `q`      | string | Case-insensitive substring match on `city`.            |
| `cursor` | string | See §1.6.                                              |
| `limit`  | int    | 1–100 (default 20).                                    |

```http
GET /api/v1/locations?q=seat&limit=20
```

Response type: `Page<Location>`.

#### `GET /api/v1/locations/{slug}` — location detail

```http
GET /api/v1/locations/seattle-wa
```

Returns a single `Location`. `404` if the slug is unknown.

#### `GET /api/v1/locations/{slug}/companies` — companies at this location

Returns every company with at least one published experience in this city,
with per-company stats **restricted to that city**.

```http
GET /api/v1/locations/seattle-wa/companies
```

Response type: `Company[]` (not paginated; cities have a bounded number of
companies). `[]` if the location exists but has no experiences yet.

---

### 3.6 Search

#### `GET /api/v1/search` — federated search

```http
GET /api/v1/search?q=amaz
```

```json
{
  "companies": [{ "slug": "amazon", "name": "Amazon" }],
  "roles":     [{ "slug": "software-engineer", "name": "Software Engineer" }],
  "locations": [{ "slug": "seattle-wa", "display_name": "Seattle, WA" }]
}
```

Returns up to 10 hits per group. An empty / missing `q` returns three empty
arrays. Use this for the global search bar / autocomplete in the header.

Response type: `SearchResponse`.

---

## 4. Frontend usage examples

### 4.1 Minimal `fetch` wrapper

```ts
const BASE = import.meta.env.VITE_API_BASE_URL;

export class ApiException extends Error {
  constructor(public readonly error: ApiError) {
    super(error.message);
  }
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(init.headers ?? {}),
    },
    ...init,
  });
  if (!res.ok) {
    const err = (await res.json().catch(() => null)) as ApiError | null;
    throw new ApiException(err ?? {
      timestamp: new Date().toISOString(),
      status: res.status,
      error: res.statusText,
      message: `HTTP ${res.status}`,
      path,
    });
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
```

### 4.2 Listing companies with cursor pagination

```ts
export async function listCompanies(params: {
  q?: string;
  industry?: string;
  sort?: 'name' | 'worth_score' | 'experience_count';
  order?: 'asc' | 'desc';
  cursor?: string;
  limit?: number;
} = {}): Promise<Page<Company>> {
  const qs = new URLSearchParams(
    Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => [k, String(v)]),
  ).toString();
  return api<Page<Company>>(`/api/v1/companies${qs ? `?${qs}` : ''}`);
}
```

### 4.3 Submitting an experience with field-level error mapping

```ts
export async function submitExperience(body: CreateExperienceRequest): Promise<Experience> {
  try {
    return await api<Experience>('/api/v1/experiences', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  } catch (e) {
    if (e instanceof ApiException && e.error.status === 400 && e.error.details) {
      // details items look like "baseSalary: must be greater than or equal to 0"
      const fieldErrors: Record<string, string> = {};
      for (const d of e.error.details) {
        const idx = d.indexOf(':');
        if (idx > 0) fieldErrors[d.slice(0, idx).trim()] = d.slice(idx + 1).trim();
      }
      throw { kind: 'validation', fieldErrors };
    }
    throw e;
  }
}
```

> **Field name casing for errors.** Validation `details[]` use the Java
> property name in `camelCase` (e.g. `baseSalary`, not `base_salary`).
> Map them back to your form fields accordingly.

---

## 5. Quick reference — endpoint table

| Method | Path                                                                | Body                          | Returns                | Notes                              |
|--------|---------------------------------------------------------------------|-------------------------------|------------------------|------------------------------------|
| GET    | `/actuator/health`                                                  | —                             | `{ status: "UP" }`     | Public health probe.               |
| GET    | `/api/hello`                                                        | —                             | `{ app, status, ... }` | Legacy smoke test.                 |
| GET    | `/api/v1/companies`                                                 | —                             | `Page<Company>`        | `q`, `industry`, `sort`, `order`, `cursor`, `limit`. |
| GET    | `/api/v1/companies/{slug}`                                          | —                             | `Company`              | 404 on unknown slug.               |
| GET    | `/api/v1/companies/{slug}/roles`                                    | —                             | `CompanyRole[]`        |                                    |
| GET    | `/api/v1/companies/{slug}/roles/{roleSlug}`                         | —                             | `CompanyRole`          |                                    |
| GET    | `/api/v1/companies/{slug}/roles/{roleSlug}/experiences`             | —                             | `Page<Experience>`     | Optional `?city=` slug filter.     |
| GET    | `/api/v1/roles`                                                     | —                             | `Role[]`               | Not paginated.                     |
| POST   | `/api/v1/experiences`                                               | `CreateExperienceRequest`     | `Experience` (201)     | Anonymous; validates per §5 of spec. |
| GET    | `/api/v1/experiences/{id}`                                          | —                             | `Experience`           |                                    |
| GET    | `/api/v1/locations`                                                 | —                             | `Page<Location>`       | `q`, `cursor`, `limit`.            |
| GET    | `/api/v1/locations/{slug}`                                          | —                             | `Location`             |                                    |
| GET    | `/api/v1/locations/{slug}/companies`                                | —                             | `Company[]`            | Stats are scoped to this city.     |
| GET    | `/api/v1/search`                                                    | —                             | `SearchResponse`       | `q` required; max 10 per group.    |

---

## 6. Things the UI should know are coming (not yet enforced)

These behaviors are in the backend spec but not enforced in v1. Build your
UI to tolerate them now and you won't need to retrofit later:

1. **Rate limiting (spec §6).** Expect `429 Too Many Requests` on
   `POST /api/v1/experiences` (5/day/IP, 1/min/IP), `GET /api/v1/search`
   (30/min/IP), other GETs (120/min/IP). Honor `Retry-After`.
2. **Turnstile / honeypot on submit (spec §6.4).** A future field
   (`turnstile_token`) will be required on `POST /api/v1/experiences`.
   Plan a hidden form field and a Cloudflare Turnstile widget.
3. **Body size cap of 64 KB** on `POST` (spec §6.5). Keep `wish_knew`
   under 2000 chars and you'll never hit this.
4. **`status = 'removed'`** experiences are excluded from all reads. The
   `status` field is still returned for completeness; the UI should not
   display anything other than `published` experiences.
5. **Auth (spec §9).** When auth ships, public endpoints stay public and
   gain optional behavior for logged-in users (voting, reporting, saved
   companies). The UI should already separate "anonymous browse" from
   "submit" flows.
