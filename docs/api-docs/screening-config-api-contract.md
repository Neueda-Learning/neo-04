# Screening Config API contract

This is **this module's own API** — not the orchestrator contract in `neo-00/api-contract.md`,
which stays untouched. It exists so the frontend and backend can be built against the same
shape before either is finished. Base path for everything below: `/api/v1/screening-configs`.

## Why "CRUD" isn't quite CRUD here

`screening_config` (see
[002-create-screening-case.yaml](../backend/src/main/resources/db/changelog/changes/002-create-screening-case.yaml))
is a table of **immutable, versioned snapshots** — `version` is the primary key, not a
surrogate id, and exactly one row has `current_version = true` at any time. `watchlist_entry`
and `country_risk_entry` hang off a `version` by foreign key. That shape means:

- **Create** = write a whole new version (its watchlist + country-risk rows included), never a
  partial insert.
- **Update** in the usual sense (editing a version's fields in place) **does not exist** — a
  published version is a fact about the past. The only mutation is **activation**: flipping
  which version is current.
- **Delete** only makes sense for a version nobody has used yet.

If a future requirement needs in-place editing of a version's rows, that is a new change set and
a new section here — do not repurpose these endpoints to do it.

## Data shapes

### `ScreeningConfigSummary` — list rows

```jsonc
{
  "version": 3,
  "samplingFrequency": 10,
  "currentVersion": true,
  "createdBy": "ops-team",
  "createdAt": "2026-07-28T09:15:00Z",
}
```

### `ScreeningConfigDetail` — single version, full detail

```jsonc
{
  "version": 3,
  "samplingFrequency": 10,
  "currentVersion": true,
  "createdBy": "ops-team",
  "createdAt": "2026-07-28T09:15:00Z",
  "watchlistEntries": [
    {
      "id": 101,
      "listId": "OFAC-SDN",
      "firstName": "Jane",
      "lastName": "Doe",
      "dateOfBirth": "1980-04-12",
      "nationality": "GB",
      "listType": "SANCTIONS",
      "source": "OFAC",
      "createdAt": "2026-07-28T09:15:00Z",
    },
  ],
  "countryRiskEntries": [
    {
      "id": 55,
      "countryCode": "IRN",
      "countryName": "Iran",
      "riskLevel": "HIGH",
    },
  ],
}
```

### `ScreeningConfigCreateRequest` — POST body

```jsonc
{
  "samplingFrequency": 10,
  "createdBy": "ops-team",
  "activate": false,
  "watchlistEntries": [
    {
      "listId": "OFAC-SDN",
      "firstName": "Jane",
      "lastName": "Doe",
      "dateOfBirth": "1980-04-12",
      "nationality": "GB",
      "listType": "SANCTIONS",
      "source": "OFAC",
    },
  ],
  "countryRiskEntries": [
    { "countryCode": "IRN", "countryName": "Iran", "riskLevel": "HIGH" },
  ],
}
```

Fields never sent by the client because the server owns them: `version` (next value, computed
server-side), `currentVersion` (only `activate` decides this), `createdAt`, and every entry `id`.

Validation: `samplingFrequency` required, positive integer. `createdBy` required, non-blank.
`watchlistEntries` / `countryRiskEntries` may be empty arrays but not `null`. Each
`countryRiskEntry.riskLevel` must be one of `LOW` · `MEDIUM` · `HIGH` (matches `risk_level`);
`countryCode` is the 3-letter ISO code and must be unique within the request (mirrors
`uk_country_risk_version_code`).

## Endpoints

### `GET /api/v1/screening-configs`

List every version, newest first, summary shape only (no nested entries — keep the list cheap).

- `200` → `ScreeningConfigSummary[]`

### `GET /api/v1/screening-configs/current`

The version currently in effect — what `ApplicationService` should be reading for live
screening.

- `200` → `ScreeningConfigDetail`
- `404` → no version has ever been activated yet (error shape below)

### `GET /api/v1/screening-configs/{version}`

One version, full detail, whether or not it is current — for reviewing history.

- `200` → `ScreeningConfigDetail`
- `404` → unknown version

### `POST /api/v1/screening-configs`

Create the next version. The server computes `version` as `max(version) + 1` (starting at `1`),
persists it with its watchlist and country-risk rows in one transaction, and sets
`created_at`. If `activate: true` is sent, the new version and the previous current version are
flipped atomically — the API caller never has to make two calls to "publish and switch".

- Request → `ScreeningConfigCreateRequest`
- `201 Created`, `Location: /api/v1/screening-configs/{version}` → `ScreeningConfigDetail`
- `400` → validation failure (missing field, bad `riskLevel`, duplicate `countryCode`)

### `PUT /api/v1/screening-configs/{version}/activate`

The only "update": make `{version}` the current one. Clears `current_version` on whatever was
current before, sets it on this one, in one transaction — never two current versions, never
zero once any version exists.

- No request body
- `200` → `ScreeningConfigDetail` (the now-current version)
- `404` → unknown version
- `409` → `{version}` is already current (nothing to do; treat as a client error, not a retry)

### `DELETE /api/v1/screening-configs/{version}`

Remove a version that was created by mistake or never went live.

- `204 No Content` on success
- `404` → unknown version
- `409` → `{version}` is the current version, **or** it is referenced by a `screening_record`
  via `config_version` (`fk_screening_record_config_version` is `ON DELETE SET NULL`, so the
  delete itself would succeed at the DB level — the API rejects it anyway so screening history
  never silently loses which config produced a decision)

Deleting a version cascades to its own `watchlist_entry` / `country_risk_entry` rows
(application-level cascade, since both FKs are `ON DELETE RESTRICT`) — never to another
version's rows.

## Error shape

Same as every other endpoint in this service — see `GlobalExceptionHandler`:

```jsonc
{
  "timestamp": "2026-07-28T09:15:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "version 3 is the current version and cannot be deleted",
}
```

## Out of scope for this contract

- Editing rows of an already-created version (add a change set + new section here if that
  requirement shows up).
- Anything under `/api/v1/applications` — that is the orchestrator contract, fixed, described
  in `neo-00/api-contract.md`.
