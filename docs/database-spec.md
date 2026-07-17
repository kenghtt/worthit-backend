# WorthIt — Backend Database Specification

This document describes the **database** that the WorthIt backend needs in
order to replace the hardcoded data currently used by the UI.

It is the companion to [`api-endpoints.md`](./api-endpoints.md) (which
describes the HTTP endpoints) and to the frontend doc
[`worthit/docs/ui-endpoint-usage.md`](../../worthit/docs/ui-endpoint-usage.md)
(which describes where each endpoint is consumed).

> Status: **planning doc**. The entity/repository classes referenced by
> `src/main/java/com/worthit/backend/seed/DataSeeder.java` are the intended
> shape of the model but are not all implemented yet. This document is the
> source of truth for what they should become.

---

## 1. Stack & conventions

| Concern        | Choice                                                      |
|----------------|------------------------------------------------------------|
| Database       | PostgreSQL (Supabase-hosted in `application-local.properties`) |
| Access         | Spring Data JPA / Hibernate                                 |
| Schema mgmt    | `spring.jpa.hibernate.ddl-auto=update` for now (Flyway later) |
| IDs            | `BIGINT` surrogate primary keys (`id`)                      |
| Slugs          | URL-safe `slug` columns, unique, generated via `SlugUtil`   |
| Money          | Whole US dollars stored as `INTEGER` (no cents)            |
| Scores         | `NUMERIC(3,1)` (0.0–10.0, one decimal place)               |
| Timestamps     | `created_at` / `updated_at` as `TIMESTAMPTZ` (UTC)         |
| Soft delete    | `active BOOLEAN NOT NULL DEFAULT true` on lookup tables    |

> **Important caveat (already noted in `application.properties`):** Hibernate
> `ddl-auto=update` does **not** create CHECK constraints, partial indexes, or
> materialized views. Anything listed below as a CHECK/partial index/view must
> be added with manual SQL or Flyway before production.

### Slug rules

Slugs follow the same rules the UI already uses (see
`worthit/src/utils/slug.js` and `worthit/docs/page_urls.md`): lowercase,
non-alphanumerics collapsed to `-`, trimmed. `SlugUtil.slugify()` on the
backend must produce identical output so URLs match on both sides.

---

## 2. Entity overview

```
company (1) ──< company_role >── (1) role
   │                                  
   │ 1                                
   ▼ *                                
 level                                
                                      
experience  ── many-to-one ──▶ company
            ── many-to-one ──▶ role
            ── many-to-one ──▶ location
            ── many-to-one ──▶ level   (nullable)
```

| Table          | Purpose                                                        |
|----------------|---------------------------------------------------------------|
| `company`      | A company users can browse / review.                          |
| `role`         | A global job role/title (e.g. Software Engineer).             |
| `company_role` | Join table: which roles exist at which company.               |
| `level`        | Per-company level ladder (e.g. Amazon SDE I/II/III).         |
| `location`     | A city/state where experiences happened.                     |
| `experience`   | A single submitted compensation + culture review.            |

Two enums are used by `experience`: `employment_status` and
`experience_status` (see §9).

---

## 3. `company`

A browsable, searchable company.

| Column         | Type            | Constraints                          | Notes                              |
|----------------|-----------------|--------------------------------------|------------------------------------|
| `id`           | BIGINT          | PK, identity                         |                                    |
| `slug`         | VARCHAR(160)    | NOT NULL, UNIQUE                     | URL key, e.g. `amazon`             |
| `name`         | VARCHAR(160)    | NOT NULL                             | Display name, e.g. `Amazon`        |
| `industry`     | VARCHAR(80)     | NULL                                 | e.g. `Tech`, `Fintech`             |
| `headquarters` | VARCHAR(160)    | NULL                                 | Free text, e.g. `Seattle, WA`      |
| `active`       | BOOLEAN         | NOT NULL DEFAULT true                | Soft delete / hide flag            |
| `created_at`   | TIMESTAMPTZ     | NOT NULL DEFAULT now()               |                                    |
| `updated_at`   | TIMESTAMPTZ     | NOT NULL DEFAULT now()               |                                    |

Indexes:
- UNIQUE (`slug`)
- INDEX on `lower(name)` (typeahead / case-insensitive search)
- INDEX on `industry` (filtering)

---

## 4. `role`

A global role/title, independent of company.

| Column       | Type         | Constraints            | Notes                                  |
|--------------|--------------|------------------------|----------------------------------------|
| `id`         | BIGINT       | PK, identity           |                                        |
| `slug`       | VARCHAR(160) | NOT NULL, UNIQUE       | e.g. `software-engineer`               |
| `name`       | VARCHAR(160) | NOT NULL               | e.g. `Software Engineer`               |
| `family`     | VARCHAR(80)  | NULL                   | Grouping, e.g. `Engineering`           |
| `active`     | BOOLEAN      | NOT NULL DEFAULT true  |                                        |
| `created_at` | TIMESTAMPTZ  | NOT NULL DEFAULT now() |                                        |
| `updated_at` | TIMESTAMPTZ  | NOT NULL DEFAULT now() |                                        |

Indexes:
- UNIQUE (`slug`)

> The seeder creates 4 base roles (`Software Engineer`, `Senior Software
> Engineer`, `Staff Engineer`, `Engineering Manager`) plus a couple of extras
> (`AI Engineer`, `Network Engineer`). The model itself is open-ended.

---

## 5. `company_role`

Which roles are offered/available at which company. Drives the role list on
the company detail page.

| Column       | Type        | Constraints                                | Notes |
|--------------|-------------|--------------------------------------------|-------|
| `id`         | BIGINT      | PK, identity                               |       |
| `company_id` | BIGINT      | NOT NULL, FK → `company(id)`               |       |
| `role_id`    | BIGINT      | NOT NULL, FK → `role(id)`                  |       |
| `active`     | BOOLEAN     | NOT NULL DEFAULT true                      |       |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now()                     |       |

Constraints / indexes:
- UNIQUE (`company_id`, `role_id`)
- INDEX (`company_id`)

---

## 6. `level`

Per-company level ladder (e.g. Amazon `SDE I` / `SDE II` / `SDE III`). Levels
are scoped to a company and ordered by `normalized_rank`.

| Column            | Type         | Constraints                          | Notes                                  |
|-------------------|--------------|--------------------------------------|----------------------------------------|
| `id`              | BIGINT       | PK, identity                         |                                        |
| `company_id`      | BIGINT       | NOT NULL, FK → `company(id)`         |                                        |
| `name`            | VARCHAR(80)  | NOT NULL                             | e.g. `SDE II`, `L4`, `E5`              |
| `normalized_rank` | SMALLINT     | NOT NULL                             | Sort order within the company (low→high) |
| `active`          | BOOLEAN      | NOT NULL DEFAULT true               |                                        |
| `created_at`      | TIMESTAMPTZ  | NOT NULL DEFAULT now()              |                                        |

Constraints / indexes:
- UNIQUE (`company_id`, `name`)
- INDEX (`company_id`, `normalized_rank`)

---

## 7. `location`

A city/state where an experience happened. One row per distinct city/state.

| Column       | Type         | Constraints            | Notes                          |
|--------------|--------------|------------------------|--------------------------------|
| `id`         | BIGINT       | PK, identity           |                                |
| `slug`       | VARCHAR(160) | NOT NULL, UNIQUE       | e.g. `seattle-wa`              |
| `city`       | VARCHAR(120) | NOT NULL               | e.g. `Seattle`                 |
| `state`      | VARCHAR(120) | NOT NULL               | e.g. `WA` (may be blank/remote)|
| `active`     | BOOLEAN      | NOT NULL DEFAULT true  |                                |
| `created_at` | TIMESTAMPTZ  | NOT NULL DEFAULT now() |                                |
| `updated_at` | TIMESTAMPTZ  | NOT NULL DEFAULT now() |                                |

Constraints / indexes:
- UNIQUE (`slug`)
- UNIQUE (`city`, `state`)

> The UI today stores location as a single string (`"Seattle, WA"`). The
> backend normalizes this into `city` + `state`; the API may still expose a
> combined `location` string for convenience (see `api-endpoints.md`).

---

## 8. `experience`

The core table: a single submitted compensation + culture review. This is what
populates the experiences list and the individual experience modal.

| Column              | Type         | Constraints                              | Notes                                                |
|---------------------|--------------|------------------------------------------|------------------------------------------------------|
| `id`                | BIGINT       | PK, identity                             |                                                      |
| `company_id`        | BIGINT       | NOT NULL, FK → `company(id)`             |                                                      |
| `role_id`           | BIGINT       | NOT NULL, FK → `role(id)`                |                                                      |
| `location_id`       | BIGINT       | NOT NULL, FK → `location(id)`            |                                                      |
| `level_id`          | BIGINT       | NULL, FK → `level(id)`                   | Optional; user may not pick a level                  |
| `level_name`        | VARCHAR(80)  | NULL                                     | Denormalized level label captured at submit time     |
| `employment_status` | ENUM         | NOT NULL                                 | See §9 (`employment_status`)                         |
| `years_experience`  | SMALLINT     | NOT NULL, CHECK (>= 0)                   | Total years of experience                            |
| `years_at_company`  | SMALLINT     | NULL, CHECK (>= 0)                       | Tenure at this company                               |
| `base_salary`       | INTEGER      | NOT NULL, CHECK (>= 0)                   | Annual base, whole USD                               |
| `bonus`             | INTEGER      | NOT NULL DEFAULT 0, CHECK (>= 0)         | Annual bonus, whole USD                              |
| `stock`             | INTEGER      | NOT NULL DEFAULT 0, CHECK (>= 0)         | Annual equity/stock (UI calls this `equity`)         |
| `signing_bonus`     | INTEGER      | NOT NULL DEFAULT 0, CHECK (>= 0)         | One-time signing bonus, whole USD                    |
| `compensation_year` | SMALLINT     | NOT NULL                                 | Year the comp applies to, e.g. `2025`               |
| `stress_level`      | NUMERIC(3,1) | NOT NULL, CHECK (0.0–10.0)               | UI calls this `stress`                               |
| `hours_per_week`    | SMALLINT     | NULL, CHECK (>= 0)                       | Typical weekly hours (see §10 hours note)            |
| `worth_it_score`    | NUMERIC(3,1) | NOT NULL, CHECK (0.0–10.0)               | UI calls this `worthScore`                           |
| `why_stay`          | TEXT         | NULL                                     | Free-text narrative                                  |
| `why_leave`         | TEXT         | NULL                                     | Free-text narrative                                  |
| `wish_knew`         | TEXT         | NULL                                     | "What I wish I knew" (UI shows as `advice`/note)     |
| `status`            | ENUM         | NOT NULL DEFAULT 'pending'               | See §9 (`experience_status`)                         |
| `created_at`        | TIMESTAMPTZ  | NOT NULL DEFAULT now()                   | Submission timestamp (UI shows as `submittedDate`)   |
| `updated_at`        | TIMESTAMPTZ  | NOT NULL DEFAULT now()                   |                                                      |

Indexes:
- INDEX (`company_id`)
- INDEX (`company_id`, `role_id`)
- INDEX (`company_id`, `role_id`, `location_id`)
- INDEX (`location_id`)
- INDEX (`status`) — so list endpoints can filter to `published` only
- INDEX (`created_at`) — cursor pagination / "most recent" ordering

> **Culture sub-scores (optional, future):** the submit form collects extra
> culture sliders (`autonomy`, `coding`, `meetings`, `firefighting`,
> `micromanagement`, `psychologicalSafety`, `feedbackQuality`,
> `growthOpportunities`, `followManager`, `reviews`). These are **not** rendered
> by the UI today. They can either be added as nullable `NUMERIC(3,1)` columns
> on `experience` or split into a separate `experience_culture` table when the
> UI starts displaying them. Left out of the core table above to avoid premature
> width; see `api-endpoints.md` §submit for how the payload maps.

---

## 9. Enums

### `employment_status`
Whether the reviewer is/was current or former at the company.

| Value     | Meaning                       |
|-----------|-------------------------------|
| `current` | Currently employed there      |
| `former`  | Previously employed there     |

### `experience_status`
Moderation lifecycle of a submitted experience.

| Value       | Meaning                                                  |
|-------------|----------------------------------------------------------|
| `pending`   | Submitted, awaiting moderation (default on insert)       |
| `published` | Visible in public list / detail endpoints                |
| `rejected`  | Hidden; failed moderation                                |

> Only `published` experiences are returned by public read endpoints and only
> they count toward aggregate stats (§10). The seeder inserts rows directly as
> `published`.

---

## 10. Derived / aggregate data

The UI shows aggregate stats (average WorthIt score, average stress, counts)
on company cards, location cards, role lists, etc. These are **not** stored
columns; they are computed from `experience` rows where `status = 'published'`.

Per **company**:
- `experience_count` = COUNT(*)
- `avg_worth_score`  = AVG(`worth_it_score`)
- `avg_stress`       = AVG(`stress_level`)
- `role_count`       = COUNT(DISTINCT `role_id`) (or count of `company_role`)

Per **company + role**:
- `experience_count`, `avg_worth_score`, `avg_stress`
- salary range (min/max/average `base_salary`)

Per **location (city)**:
- `experience_count`, `company_count` (DISTINCT `company_id`)
- `avg_worth_score`, `avg_stress`

Implementation options (pick later): compute on the fly with SQL aggregates
(fine at this data size), or add a materialized view refreshed periodically.
`ddl-auto` will not create a materialized view, so that path needs Flyway/SQL.

**Hours note:** the UI renders an hours *range* (`hoursMin`–`hoursMax`) per
experience, but the model stores a single `hours_per_week`. Decide one of:
(a) keep a single value and have the API return `hoursMin === hoursMax`, or
(b) add `hours_min` / `hours_max` columns. Recommended: keep single
`hours_per_week` and adapt the UI, or expose both equal in the API for now.

---

## 11. Seeding

`DataSeeder` (gated by `app.seed.enabled`, currently `false`) is idempotent
(upsert-by-slug / by natural key) and will:
1. Seed companies (a large catalog; only ~20 demo companies get experiences).
2. Seed the base roles + a couple of extras.
3. Seed `company_role` links.
4. Seed one `location` per distinct company HQ.
5. Seed per-company `level` ladders for common employers.
6. Seed ~5 `experience` rows per demo company (status `published`).

To use it, implement the entities/repositories listed in §2 and flip
`app.seed.enabled=true`.

---

## 12. Build order (suggested)

1. Create the 6 entities + 2 enums above (`@Entity` / `@Enumerated`).
2. Create the matching Spring Data repositories (method names already implied
   by `DataSeeder`, e.g. `findBySlug`, `findByCityAndState`,
   `findByCompany_IdOrderByNormalizedRankAsc`, `countByCompany_Id`,
   `existsByCompany_IdAndRole_Id`).
3. Add `SlugUtil` in `com.worthit.backend.util`.
4. Enable seeding and verify rows land in Postgres.
5. Build the read endpoints, then the submit endpoint (see `api-endpoints.md`).
6. Before production: add CHECK constraints, indexes, and any view via Flyway.
