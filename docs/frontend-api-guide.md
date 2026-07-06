# WorthIt Backend — Frontend API Guide

Handoff doc for the WorthIt web UI. Describes **how to call each backend endpoint**, what
it returns, and **which screens** should use it.

Backend contract reference (implementation details): [`api-endpoints.md`](./api-endpoints.md).

UI screen mapping (what still uses mock data): `worthit/docs/ui-endpoint-usage.md`.

---

## Quick start

| Setting | Value |
|---------|-------|
| Dev base URL | `http://localhost:8080` |
| Env var (Vite) | `VITE_API_BASE_URL` |
| API prefix | `/api/v1` (except health — see §Health) |
| Auth | **None** — all endpoints are public today |
| Request / response type | `application/json` |

Example using the existing client (`worthit/src/lib/apiClient.js`):

```js
import { api, buildQuery } from '../lib/apiClient';

// GET with query params
const page = await api(`/api/v1/companies${buildQuery({ sort: 'worthScore', order: 'desc', limit: 10 })}`);

// POST with body
const created = await api('/api/v1/experiences', {
  method: 'POST',
  body: JSON.stringify(formData),
});
```

On HTTP errors, `api()` throws `ApiException` with the backend error envelope (see
[Errors](#errors)).

---

## Conventions

### Pagination

List endpoints return a page envelope:

```json
{
  "items": [ /* ... */ ],
  "next_cursor": "opaque-string-or-null"
}
```

| Param | Meaning |
|-------|---------|
| `limit` | Page size. Default **20**, max **50** (company search default **8**, max **20**). |
| `cursor` | Opaque token from the previous response's `next_cursor`. Omit on the first page. |
| `next_cursor` | Pass as `cursor` for the next page. `null` on the last page. |

Read results from **`items`** (the typeahead hook also falls back to `data` for safety).

### Slugs

Path params (`{slug}`, `{roleSlug}`) are URL-safe slugs (e.g. `amazon`, `software-engineer`,
`seattle-wa`). They must match the UI's `slug()` helper output.

Route params like `/companies/:companyId` are these slugs, not numeric IDs.

### JSON field naming

Most list/detail DTOs use **camelCase** in JSON (`experienceCount`, `avgWorthScore`, …).

**Exception — experience payloads** (`GET …/experiences`, `POST /api/v1/experiences`
response) use **snake_case** because they mirror DB columns:

| JSON key | Meaning |
|----------|---------|
| `worth_it_score` | WorthIt score 0–10 |
| `stress_level` | Stress 0–10 |
| `base_salary`, `bonus`, `stock`, `signing_bonus` | Whole USD |
| `employment_status` | `"current"` or `"past"` (see note below) |
| `created_at` | ISO-8601 UTC timestamp |
| `why_stay`, `why_leave`, `wish_knew` | Free text (nullable) |

**POST request bodies** use **camelCase** (`worthItScore`, `employmentStatus`, …).

**No internal IDs:** experience objects do **not** include a database `id`. Use a composite
key for React lists, e.g. `` `${created_at}-${company_slug}-${role_slug}-${location_slug}` ``.

**Employment status:** send `"current"` or `"former"` on POST. Responses return `"current"`
or `"past"` (not `"former"`).

### Errors

Non-2xx responses return:

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

Validation failures (`400`) populate `details` with a string array, e.g.
`["baseSalary: must be greater than or equal to 0"]`.

Common statuses: `404` missing slug, `400` validation, `201` created experience.

---

## Endpoint quick reference

| Method | Path | Purpose | Primary UI screen(s) |
|--------|------|---------|----------------------|
| GET | `/api/v1/companies` | List/search companies with aggregate stats | Companies list, Home featured |
| GET | `/api/v1/companies/search` | Lightweight company typeahead | Home search bar ✅ live |
| GET | `/api/v1/companies/{slug}` | Company profile (no stats) | Company detail header |
| GET | `/api/v1/companies/{slug}/roles` | Roles at company + per-role stats | Company detail role cards |
| GET | `/api/v1/experiences` | Published experiences filtered by company + role | Experiences list, experience modal |
| GET | `/api/v1/locations` | List/search cities with stats | Locations list, Home |
| GET | `/api/v1/locations/{slug}` | Single city stats | Location detail header |
| GET | `/api/v1/locations/{slug}/companies` | Companies with experiences in that city | Location detail company list |
| GET | `/api/v1/roles` | Global role picker options | Submit form |
| GET | `/api/v1/companies/{slug}/levels` | Per-company level picker options | Submit form |
| POST | `/api/v1/experiences` | Submit a new experience | Submit form |
| GET | `/api/hello` | Liveness / smoke test | Dev/CI only (not a UI screen) |

Existing UI API modules: `companyApi.js`, `locationApi.js`. Still needed:
`experienceApi.js` (submit + optional shared fetch helpers), plus wrappers for `/roles`
and `/levels`.

---

## Companies

### List / search companies

**Purpose:** Full companies browse page — filter, sort, and paginate companies with
aggregate stats from **published** experiences.

```
GET /api/v1/companies
```

| Query param | Type | Description |
|-------------|------|-------------|
| `q` | string | Case-insensitive substring on company name |
| `includeZeroExperience` | boolean | Include companies with `experienceCount = 0`. Default `false` for browse mode; set `true` when user is actively searching. |
| `industry` | string | Exact industry filter (e.g. `Tech`) |
| `sort` | string | `name` (default), `worthScore`, `experiences`, `stress` |
| `order` | string | `asc` (default) or `desc` |
| `cursor`, `limit` | | Pagination |

**Response:** `Page<CompanySummary>`

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
      "avgStress": 6.8,
      "avgHoursPerWeek": 46.5,
      "avgTotalComp": 185000
    }
  ],
  "next_cursor": null
}
```

`avgHoursPerWeek` (mean reported weekly hours, one decimal, `null` if none) and
`avgTotalComp` (mean of `base_salary + bonus + stock + signing_bonus` per
experience, whole USD, `null` when no published experiences) populate the
companies table's **Hours/week** and **Median Comp** columns.

**UI:** `CompaniesPage`, Home featured companies.

**Client:** `listCompanies(params)` in `companyApi.js`.

**Recommended usage:**
- Default browse list (no search text): send `includeZeroExperience=false` so the table does not get flooded by companies without published experiences.
- Search mode (search text present): send `includeZeroExperience=true` so manually searched companies can still appear even when their current `experienceCount` is `0`.

> **Backend note:** the controller currently binds the search string as query param
> `companySubstring`, not `q`. Until that is aliased, pass `companySubstring` or fix the
> backend to accept `q` as documented here and in `api-endpoints.md`.

---

### Company search (typeahead)

**Purpose:** Fast autocomplete as the user types in the search bar. Returns **basic
profiles only** — no aggregate stats. Prefer this over `GET /companies` for per-keystroke
calls.

```
GET /api/v1/companies/search?q=amaz&limit=8
```

| Query param | Type | Description |
|-------------|------|-------------|
| `q` | string | Substring match (empty → empty `items`) |
| `limit` | int | Default **8**, max **20**. Single page; `next_cursor` is always `null`. |

**Response:** `Page<CompanyDetail>` — each item: `slug`, `name`, `industry`, `headquarters`.

**UI:** Home search bar — **already live** via `useCompanyTypeahead` → `suggestCompanies()`.

---

### Company detail

**Purpose:** Company header on the detail page — name, industry, HQ. Intentionally
**no** aggregate stats (those live on role cards and list views).

```
GET /api/v1/companies/{slug}
```

**Response:** single `CompanyDetail` (same four fields as typeahead items).

**Errors:** `404` if slug unknown.

**UI:** `CompanyDetail.jsx` — replace hardcoded `'Amazon'`.

**Client:** `getCompany(slug)`.

---

### Company roles

**Purpose:** Role cards on the company page — each role offered at the company with stats
from **published** experiences at that company.

```
GET /api/v1/companies/{slug}/roles?cursor=&limit=
```

**Response:** `Page<RoleSummary>`

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

Roles with no published experiences still appear with `experienceCount: 0` and `null`
salary/score fields.

**Errors:** `404` if company slug unknown.

**UI:** `CompanyDetail.jsx` role grid → links to `/companies/{slug}/roles/{roleSlug}`.

**Client:** `getCompanyRoles(slug)`.

---

### Experiences for company + role

**Purpose:** Paginated list of **published** experiences for a company/role pair. Powers
the experiences list and the individual experience modal (pass an item from the list — no
separate detail endpoint).

```
GET /api/v1/experiences?company=&role=&city=&cursor=&limit=
```

This endpoint lives on the experiences resource; company and role are passed as
query params (slugs) rather than path segments.

| Query param | Type | Description |
|-------------|------|-------------|
| `company` | string | Company **slug** (e.g. `amazon`) |
| `role` | string | Role **slug** (e.g. `software-engineer`) |
| `city` | string | Optional location **slug** filter (e.g. `seattle-wa`) |
| `cursor`, `limit` | | Pagination; sorted newest first |

**Response:** `Page<ExperienceSummary>` (snake_case fields — see [Conventions](#json-field-naming)).

**Errors:** `404` if company or role slug invalid, or role not linked to company. Unknown
`city` slug → empty page (not 404).

**UI:** `ExperiencesList.jsx`, `IndividualExperienceModal`, `RoleDetail.jsx`.

**Client:** `listExperiences(params)` in `experienceApi.js` (e.g. `listExperiences({company, role, city})`).

**Field mapping for existing components** (if not updating component prop names):

| Component expects | API field |
|-------------------|-----------|
| `worthScore` | `worth_it_score` |
| `stress` | `stress_level` |
| `equity` | `stock` |
| `hoursMin` / `hoursMax` | `hours_per_week` (single value today) |
| `whatWasItLike` | `why_stay` / `why_leave` |
| `advice` | `wish_knew` |
| `submittedDate` | format from `created_at` |
| `location` | `` `${city}, ${state}` `` |

---

## Locations

### List / search locations

**Purpose:** Locations browse page and Home location chips — cities with aggregate stats
from **published** experiences.

```
GET /api/v1/locations?q=&includeZeroExperience=&cursor=&limit=
```

| Query param | Type | Description |
|-------------|------|-------------|
| `q` | string | Case-insensitive substring on city name |
| `includeZeroExperience` | boolean | Include cities with `experienceCount = 0`. Default `false` for browse mode; set `true` when user is actively searching. |

**Response:** `Page<LocationSummary>`

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

**UI:** `LocationsPage`, Home featured locations.

**Client:** `listLocations(params)`.

**Recommended usage:**
- Default browse list (no search text): send `includeZeroExperience=false` so the table does not get flooded by cities without published experiences.
- Search mode (search text present): send `includeZeroExperience=true` so manually searched cities can still appear even when their current `experienceCount` is `0`.

---

### Location detail

**Purpose:** Header stats for a single city page.

```
GET /api/v1/locations/{slug}
```

**Response:** single `LocationSummary` (same shape as list items).

**Errors:** `404` if slug unknown.

**UI:** `LocationPage.jsx` — route param `city` is the location slug.

**Client:** `getLocation(slug)`.

---

### Companies in a location

**Purpose:** Companies that have **published** experiences in this city, with stats
scoped to that city.

```
GET /api/v1/locations/{slug}/companies?cursor=&limit=
```

**Response:** `Page<LocationCompanySummary>`

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

**Errors:** `404` if location slug unknown.

**UI:** `LocationPage.jsx` company list — replaces `companiesInSF`.

**Client:** `getLocationCompanies(slug)`.

---

## Submit experience

### Create experience

**Purpose:** Persist a user submission from the multi-step submit form. New rows are
saved as **`pending`** — they do **not** appear in any read endpoint until moderated/
published.

```
POST /api/v1/experiences
Content-Type: application/json
```

**Request body (camelCase):**

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

| Rule | Detail |
|------|--------|
| Company | Provide `companySlug` and/or `company` (display name). Backend find-or-creates. |
| Role | Provide `role`, `roleSlug`, and/or `customRole`. |
| Required fields | `city`, `employmentStatus`, `yearsExperience`, `baseSalary`, `compensationYear`, `stressLevel`, `worthItScore` |
| `employmentStatus` | `"current"` or `"former"` |
| Scores | `stressLevel`, `worthItScore` in 0.0–10.0 |
| Money fields | Whole USD, `>= 0` |
| Culture sliders | UI may send `autonomy`, `coding`, etc. — backend ignores them for now |

**Response:** `201 Created` — same snake_case shape as [Experiences for company + role](#experiences-for-company--role).

**Errors:** `400` with `details` array on validation failure.

**UI:** `SubmitExperience.jsx` — replace `console.log(formData)`; on success navigate to `/`.

**Suggested client** (`experienceApi.js`):

```js
export function submitExperience(payload) {
  return api('/api/v1/experiences', { method: 'POST', body: JSON.stringify(payload) });
}
```

---

## Form lookup endpoints

Support dropdowns on the submit form. Optional (free text works) but recommended.

### Global roles

**Purpose:** Populate the role picker with known roles.

```
GET /api/v1/roles?cursor=&limit=
```

**Response:** `Page<RoleLookupSummary>` — `slug`, `name`, `family`. Name-sorted.

**UI:** Submit form role step; optional Home role chips.

---

### Company levels

**Purpose:** Level/title dropdown after a company is selected.

```
GET /api/v1/companies/{slug}/levels?cursor=&limit=
```

**Response:** `Page<LevelSummary>` — `name`, `normalizedRank` (ascending).

**Errors:** `404` if company slug unknown.

**UI:** Submit form level step.

---

### Company typeahead (submit form)

Reuse **`GET /api/v1/companies/search?q=&limit=8`** (§Company search) for the company
picker — not the full `GET /companies` list.

---

## Health

**Purpose:** Confirm the backend process is running. **Not** a database readiness check.
Useful for local smoke tests and deploy probes — the UI does not need to call this.

```
GET /api/hello
```

**Response:** `200 OK`

```json
{
  "app": "worthit-backend",
  "status": "ok",
  "message": "Hello from WorthIt backend"
}
```

Note: this path is `/api/hello`, not under `/api/v1`.

---

## Suggested migration order

1. Companies list → `GET /api/v1/companies`
2. Company detail + roles → `{slug}` + `{slug}/roles`
3. Experiences list + modal → `/experiences?company=&role=`
4. Locations list + detail → `/locations`, `/locations/{slug}`, `/locations/{slug}/companies`
5. Home featured sections
6. Submit form → `POST /api/v1/experiences` + `/roles` + `/levels` pickers

For each screen: add a fetch (or hook), handle loading/error via `ApiException`, map
response fields to what components already render (see experience mapping table above).

---

## Integration checklist

- [ ] Set `VITE_API_BASE_URL=http://localhost:8080` in UI `.env`
- [ ] Ensure CORS origin is allowed (`http://localhost:5173` or `3000`)
- [ ] Wire remaining pages off mock data (see `ui-endpoint-usage.md`)
- [ ] Add `experienceApi.js` for submit
- [ ] Map experience **snake_case** responses (or update components)
- [ ] Stop using `exp.id` as React key — use composite keys
- [ ] After submit, show success without expecting the new row in list views (pending)
- [ ] Align `GET /companies` name filter param (`q` vs `companySubstring`) with backend

---

## Auth (future)

All endpoints are **public** today. JWT auth may be added later (likely protecting
`POST /api/v1/experiences` first). No `Authorization` header is required for now.
