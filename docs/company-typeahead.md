### Company typeahead (frontend integration guide)

This guide explains how the frontend should implement the company name typeahead used when a user creates an experience and starts typing the company field.

#### Summary
- Use the existing endpoint `GET /api/v1/companies` with the `q` parameter to fetch matching companies.
- Matching is case-insensitive and should work for substrings (e.g., typing `ok` can match `Okta` and `TikTok`).
- Request a small page size (e.g., `limit=5` to `10`) for snappy UX.
- The response is paginated; for typeahead you typically only need the first page.

---

### API

#### Endpoint
```
GET /api/v1/companies
```

#### Query parameters
- `q` (string, optional): The user-typed search string. Case-insensitive, substring match against company name.
- `limit` (number, optional): Max number of results to return. Suggested: `5`–`10`.
- Other supported filters exist (`industry`, `sort`, `order`, `cursor`) but are not needed for typeahead.

#### Response
The endpoint returns a `PageResponse<CompanyDTO>` with at least these fields.

Example (shape only; fields may include additional properties):
```json
{
  "data": [
    { "name": "Okta",   "slug": "okta" },
    { "name": "TikTok", "slug": "tiktok" }
  ],
  "cursor": null,
  "hasMore": false
}
```

---

### Request examples

Basic fetch (first page only):
```
GET /api/v1/companies?q=ok&limit=5
```

`curl` example:
```bash
curl -s \
  -G 'https://<your-host>/api/v1/companies' \
  --data-urlencode 'q=ok' \
  --data-urlencode 'limit=5'
```

JavaScript/TypeScript example:
```ts
type Company = { name: string; slug: string };
type PageResponse<T> = { data: T[]; cursor?: string | null; hasMore?: boolean };

async function fetchCompanies(q: string, limit = 5): Promise<Company[]> {
  const params = new URLSearchParams({ q, limit: String(limit) });
  const res = await fetch(`/api/v1/companies?${params.toString()}`);
  if (!res.ok) throw new Error(`Companies request failed: ${res.status}`);
  const body = (await res.json()) as PageResponse<Company>;
  return body.data;
}
```

---

### Frontend behavior checklist

- Debounce requests by ~250 ms to reduce chatter while typing.
- Only start querying when input length ≥ 2 (to avoid overly broad matches).
- Always pass `limit` (5–10) to keep payloads small and fast.
- Render each suggestion using `company.name` as the label; retain `company.slug` internally.
- Selection handling:
  - If the user clicks a suggestion → set visible `name` and store hidden `companySlug` with the selected `slug`.
  - If the user chooses to add a new company → set the visible `name` and leave `companySlug` empty.
- Provide an inline option like: `Add "<current text>"` when:
  - there is no exact case-insensitive match of `name === currentText`, or
  - the user wants to create a new company regardless of suggestions.
- Keyboard UX: Up/Down to navigate, Enter to select, Esc to close, Tab to commit current value.
- Accessibility: Ensure ARIA roles for combobox/listbox, announce result count to screen readers.

---

### Sorting recommendations
- Prefer results that start with the query first, then other substring matches.
- If you have popularity/frequency signals, sort by those as a secondary key.

---

### Edge cases & normalization
- Trim leading/trailing whitespace from the query.
- Normalize case for comparisons (backend matching is case-insensitive).
- Consider collapsing multiple spaces and ignoring punctuation when presenting the "Add" option.
- Handle diacritics consistently (e.g., use Unicode normalization on the client if needed).

---

### Error handling & empty states
- On network/server error: show a non-blocking message and allow manual entry.
- When no suggestions: show `No matches` and the `Add "<current text>"` option.
- Rate limiting: If the server rate-limits, back off (exponential backoff) and continue to allow manual entry.

---

### Caching and performance
- Cache recent queries in-memory for the current session so moving the cursor or retyping doesn't refetch.
- Use request cancellation (AbortController) to ignore stale responses when the user types quickly.
- Keep suggestion items minimal; you usually need only `name` and `slug` for the dropdown.

---

### Optional backend refinement (future)
- If payload size becomes a concern, we can add a dedicated `GET /api/v1/companies/suggest?q=…&limit=…` that returns only `{ name, slug }`.
- If current matching ever changes from case-insensitive contains, coordinate with backend to keep UX consistent.

---

### QA checklist
- Typing `ok` returns items like `Okta` and `TikTok` in the first 5–10 suggestions.
- Exact-name entry correctly sets the visible name and the stored `companySlug` when selected from the list.
- Choosing `Add "<value>"` sets only the visible name and leaves `companySlug` empty for creation later.
- Works with keyboard-only interaction and meets accessibility acceptance.

---

### Related endpoints
- `GET /api/v1/companies` — list/paginate companies (used for typeahead with `q`).
- `GET /api/v1/companies/{slug}` — fetch single company details (after selection, if needed).
