# WorthIt — Application Overview

## 1) What WorthIt is

WorthIt is a community-driven platform that helps people evaluate whether a job at a specific company is actually “worth it.”

Instead of relying on polished employer branding or incomplete salary anecdotes, WorthIt centers on real employee-submitted experiences: compensation, role/level context, workload, stress, and an explicit “would you do it again?” signal (the **worth score**).

In short, WorthIt turns scattered, hard-to-compare career stories into a structured dataset users can browse, filter, and analyze.

---

## 2) Core purpose

WorthIt exists to answer practical job-seeker questions such as:

- “Is this company a good tradeoff for pay vs stress?”
- “How different is this role at Company A vs Company B?”
- “Do people in this city/level seem satisfied with the deal?”
- “Would current/former employees choose the same path again?”

The product goal is decision support, not hype:

- make compensation and culture tradeoffs more transparent,
- reduce guesswork in career decisions,
- and surface signal from real experiences over marketing claims.

---

## 3) Who the app is for

Primary audiences:

- **Job seekers / candidates** comparing offers or planning next moves.
- **Early- to mid-career professionals** benchmarking role/level trajectories.
- **Career switchers** evaluating whether a company/role combo aligns with their priorities.

Secondary audiences:

- **Curious insiders** validating whether their own experience is typical.
- **Community contributors** sharing experiences to improve data quality for others.

---

## 4) What users can do

At a high level, users can:

1. **Browse companies** and see aggregate stats (experience count, average worth score, stress, etc.).
2. **Search and filter** by company name/industry with sorting and pagination.
3. **Open company details** and drill down into role-level views.
4. **Read published experiences** with compensation and context fields.
5. **Submit a new experience** (through write APIs/workflows) to contribute data.

This creates a loop:

- more submissions → stronger aggregates,
- stronger aggregates → better decision quality for future users.

---

## 5) Product philosophy and data principles

WorthIt is designed around a few core principles:

### 5.1 Real-world context matters

Compensation numbers without context are misleading. WorthIt pairs pay with role, level, location, workload, stress, and sentiment.

### 5.2 Aggregates should be explainable

Company/role-level metrics come from published experiences and are intended to be easy to reason about (count, average values, bounded scales).

### 5.3 Browse quality by default

The default companies browse experience prioritizes meaningful signal (for example, excluding no-data companies unless explicitly requested in search scenarios).

### 5.4 Public-read API for now

Current backend policy keeps read endpoints public while auth infrastructure is in place for future tightening.

---

## 6) System overview (frontend + backend)

WorthIt is split into two repos/apps:

- **`worthit`**: frontend UI (React-based), user flows, and API consumption.
- **`worthit-backend`**: Spring Boot API serving company, role, level, location, and experience data.

Current integration model:

- UI calls backend over HTTP JSON (`/api/v1/...`).
- Backend reads/writes relational data via Spring Data JPA/Hibernate.
- List endpoints use cursor pagination (`items` + `next_cursor`) for scalable incremental loading.

---

## 7) What this backend specifically does

`worthit-backend` is the source of truth for:

- **Company discovery APIs** (list/search/detail, role/level drilldowns).
- **Experience retrieval APIs** (published experiences by company/role, plus feeds and filters).
- **Lookup/reference data APIs** (locations and other option lists used by UI forms).
- **Experience submission APIs** (create flow with validation and moderation-ready status handling).
- **Shared error handling + response conventions** used consistently across endpoints.

The backend also centralizes business rules around:

- active/inactive entities,
- published vs non-published experiences,
- pagination limits and sort behavior,
- and aggregate metric calculation.

---

## 8) Domain model at a glance

Core entities include:

- `company`
- `role`
- `company_role` (join)
- `level` (company-specific ladders)
- `location`
- `experience`

This model allows WorthIt to represent the reality that role expectations, level naming, and outcomes differ by company and location.

---

## 9) Typical user journey (example)

1. User opens the Companies page.
2. UI requests paginated companies from backend.
3. User applies search/filter/sort; UI re-queries backend.
4. User selects a company to view detail + available roles/levels.
5. User opens a role experience list to inspect real reports.
6. User optionally submits their own experience, which enters the lifecycle managed by backend status rules.

The backend supports this journey with predictable contracts, stable slugs/URLs, and consistent response shapes.

---

## 10) Current state and direction

WorthIt is in an active build phase where backend contracts and frontend integration are being tightened.

Current emphasis:

- API completeness for all major UI screens,
- clean pagination/filter/sort semantics,
- and clear, shared documentation between frontend and backend.

Planned evolution (high level):

- stronger auth/authorization enforcement,
- schema migrations and production hardening,
- richer analytics/aggregates,
- and expanded quality/moderation workflows for submissions.

---

## 11) How to read the rest of the docs

Use this overview as the “why/what” layer, then go deeper with:

- `docs/api-endpoints.md` — endpoint-level contract and payloads.
- `docs/frontend-api-guide.md` — frontend consumption guidance.
- `docs/database-spec.md` — database entities, constraints, and conventions.

Together, these docs describe WorthIt from product intent down to implementation details.