# UC-02 · Review Alert — implementation plan (backend + frontend)

> Hand-written, unlike its sibling [uc-02-review-alert.md](uc-02-review-alert.md) ("AI
> implementation brief... regenerate, don't hand-edit"). This file **is** meant to be hand-edited
> as the work progresses — check items off, adjust as reality disagrees with the plan.

## 1. What UC-02 actually asks for

Per the brief: a bank employee opens a screening alert and sees **which watchlist entry it
matched and why** — normalised name, every candidate considered (hits *and* near-misses), the
country-risk check, the sampling flag — not just a bare `HIT`/`REVIEW`/`CLEAR`. It is **read-only**:
the row was written once by the matcher at `/execute` time; opening the alert never re-matches.

## 2. Where the repo already stands (gap analysis)

The matching engine itself (`ScreeningMatcher` + the `HIT`/`REVIEW`/`CLEAR` precedence, including
sampling) is done and correct — see the matching-logic discussion earlier in this thread. What's
missing is entirely the **read side**: computing the evidence is finished, *exposing* it is not.

| Piece | State | Detail |
|---|---|---|
| Matcher + sampling logic | ✅ done | [ScreeningMatcher.java](../../backend/src/main/java/com/neobank/module/service/matching/ScreeningMatcher.java), [ApplicationService.applyDecision](../../backend/src/main/java/com/neobank/module/service/ApplicationService.java) |
| Evidence persisted as JSON | ✅ done | `ScreeningRecord.evidence`, a `TEXT` column holding the serialised `MatchEvidence` |
| Evidence includes `sampling` | ❌ gap | `MatchEvidence` has `normalisedName`/`candidates`/`countryRisk` only; the contract's `matchResults.sampling` sub-object doesn't exist yet, and evidence is serialised *before* the sampling decision is made |
| `GET` detail endpoint | ❌ gap | Only `POST /api/v1/applications` and `GET /api/v1/applications` (list) exist. No single-record read |
| Frontend already expects it | ⚠️ half-built | `api.js` already stubs `getApplication(id)` → `GET /api/v1/applications/{id}` and `searchCases(keyword)` → `GET /api/v1/applications/cases?keyword=...`, and `CasesScreen.jsx` is wired to call `searchCases` — **both endpoints 404 today** |
| Evidence rendered anywhere | ❌ gap | No detail/evidence component exists yet; `CasesScreen`'s table has no row-click |

Two use cases share this gap, and it's worth being explicit about the boundary so this plan
doesn't quietly grow into someone else's:

- **UC-01 (Search Cases)** owns `searchCases`/the board's keyword search. It is **out of scope**
  here — flagged only because `CasesScreen` already calls it, so the "Cases" screen won't show
  any rows to click into until UC-01's search endpoint exists too. If UC-01 isn't being planned
  separately, say so and this plan can absorb a minimal version of it.
- **UC-02 (this plan)** owns the **detail view**: `GET /api/v1/applications/{id}` and the panel
  that renders its evidence.

## 3. Backend plan

### 3.1 Add `sampling` to the evidence shape

`MatchEvidence` currently has no room for the sampling outcome, and `writeEvidence()` is called
*before* `applyDecision()` decides whether this row was sampled — so today's evidence JSON can
never reflect it even after the field exists. Fix both:

- New record, next to the existing evidence types:
  ```java
  // com.neobank.module.service.matching.SamplingEvidence
  public record SamplingEvidence(boolean sampled, Long position) {}
  ```
- Extend `MatchEvidence` with a fourth component: `SamplingEvidence sampling`.
- In `ApplicationService.decide()`, reorder so the sampling decision is known *before* the
  evidence is serialised: compute `applyDecision(...)`'s `sampled`/row-id first, build the
  `MatchEvidence` (or a copy of it) with that `SamplingEvidence` attached, *then* call
  `writeEvidence()` once, so the stored blob and the reported outcome never disagree.

This is a Java-only, JSON-shape change — **no Liquibase changeset needed**, since `evidence` is
already a schema-agnostic `TEXT` column.

### 3.2 New DTO: `ScreeningRecordDetailView`

Mirrors `ScreeningRecordView` (the list DTO) but adds the fields the detail screen needs. Return
the evidence as a real nested object, not a re-escaped string — see the earlier discussion in this
thread on why a `JsonNode`/parsed object is required for the frontend to get clean nested JSON:

```java
public record ScreeningRecordDetailView(
        String applicationId,
        String machineOutcome,
        String finalOutcome,
        String processingStatus,
        String callbackStatus,
        String reasonCode,
        Integer configVersion,
        JsonNode evidence,      // parsed once here, not a stringified blob
        Instant createdAt,
        Instant updatedAt) {

    public static ScreeningRecordDetailView of(ScreeningRecord row, ObjectMapper json) {
        JsonNode evidence = row.getEvidence() == null
                ? null : parseQuietly(json, row.getEvidence());
        return new ScreeningRecordDetailView(row.getApplicationId(), row.getMachineOutcome(),
                row.getFinalOutcome(), row.getProcessingStatus(), row.getCallbackStatus(),
                row.getReasonCode(), row.getConfigVersion(), evidence,
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
```

### 3.3 New endpoint: `GET /api/v1/applications/{applicationId}`

In `ApplicationController` (same `@RequestMapping("/api/v1/applications")`, no new controller
needed — matches the existing "one controller, this module's whole HTTP surface" doc comment):

```java
@GetMapping("/{applicationId}")
public ScreeningRecordDetailView get(@PathVariable String applicationId) {
    return applications.findOne(applicationId);
}
```

`ApplicationService.findOne(applicationId)` calls `screeningRecords.findByApplicationId(id)
.map(row -> ScreeningRecordDetailView.of(row, json)).orElseThrow(() -> new
NoSuchElementException("no case for " + applicationId))`. `NoSuchElementException` already maps to
`404` via the existing `GlobalExceptionHandler.handleNotFound` — no new exception handler needed.

This satisfies both `api.js`'s existing `getApplication(id)` stub **and** the path `CasesScreen`
would use for a row-click, without inventing a bare `/cases/{id}` path that conflicts with this
repo's fixed `/api/v1/applications` prefix (per `AGENTS.md`: adapt the UC's intent to the existing
fixed contract, don't add a parallel endpoint shape).

### 3.4 Tests to add

- `ApplicationServiceTest`: `findOne` returns the detail view for an existing row; throws
  `NoSuchElementException` for an unknown id; evidence JSON round-trips with a populated
  `sampling` object once a row is sampled.
- `ApplicationControllerTest`: `GET /api/v1/applications/{id}` → 200 with the expected shape for a
  seeded row; → 404 with the standard error body for an unknown id.
- Keep `./mvnw test` green (H2) before touching `*IT`/Testcontainers.

## 4. Frontend plan

Per `design-system/DESIGN.md` §5, this is the **Detail** archetype: `PageHeader` (badge + meta) →
`Split` → one toned `Card` per evidence section in the main column, applicant/summary `KeyValue` in
the sidebar, a `Caption` stating evidence is replayed, never re-matched.

### 4.1 New component: `components/AlertDetail.jsx`

```jsx
<PageHeader
  title={`Case ${applicationId}`}
  badge={<Badge tone={statusTone(detail.finalOutcome)}>{detail.finalOutcome}</Badge>}
  meta={`machine outcome ${detail.machineOutcome} · config v${detail.configVersion ?? '—'} · ${time(detail.createdAt)}`}
/>
<Split
  main={
    <Stack gap={4}>
      <Card tone={detail.evidence?.candidates?.length ? 'warning' : 'positive'}>
        <h3>Watchlist candidates</h3>
        <DataTable
          columns={[
            { key: 'entryId', header: 'Entry', mono: true },
            { key: 'rule', header: 'Rule' },
            { key: 'verdict', header: 'Verdict' },
            { key: 'weight', header: 'Weight' },
            { key: 'matchedFields', header: 'Matched fields',
              render: (c) => c.matchedFields.join(', ') },
          ]}
          rows={detail.evidence?.candidates ?? []}
          rowKey={(c) => c.entryId}
          empty={<EmptyState title="No candidates considered" />}
        />
      </Card>
      <Card tone={detail.evidence?.countryRisk?.highRisk ? 'warning' : 'positive'}>
        <h3>Country risk</h3>
        <KeyValue items={[
          ['Country', detail.evidence?.countryRisk?.countryCode ?? '—'],
          ['High risk', String(detail.evidence?.countryRisk?.highRisk ?? false)],
        ]} />
      </Card>
      <Card tone={detail.evidence?.sampling?.sampled ? 'warning' : 'positive'}>
        <h3>Sampling</h3>
        <KeyValue items={[
          ['Sampled', String(detail.evidence?.sampling?.sampled ?? false)],
          ['Position', detail.evidence?.sampling?.position ?? '—'],
        ]} />
      </Card>
    </Stack>
  }
  sidebar={
    <>
      <KeyValue items={[
        ['Application', detail.applicationId],
        ['Reason code', <Tag key="rc">{detail.reasonCode}</Tag>],
        ['Callback', detail.callbackStatus],
      ]} />
      <Caption>Evidence replayed from the stored record — never re-matched on open.</Caption>
    </>
  }
/>
```

Reuses only existing design-system components (`PageHeader`, `Split`, `Card`, `DataTable`,
`KeyValue`, `Badge`, `Tag`, `Caption`, `EmptyState`, `Stack`) — no new dependency, per
`DESIGN.md` §8's escalation order.

### 4.2 Wire it into `CasesScreen.jsx`

Add `onRowClick={(r) => openDetail(r.applicationId)}` to the existing `DataTable`, track a
`selectedId` state, fetch via `api.getApplication(selectedId)` (already stubbed in `api.js`), and
render `<AlertDetail .../>` either as a `Modal` (`wide`) or as a second screen state — a `Modal`
keeps the board underneath, consistent with how `ScreeningConfigScreen.jsx` already uses `Modal`
for a "look closer without leaving the list" interaction.

### 4.3 `api.js`

No change needed — `getApplication(id)` is already correctly shaped for §3.3's endpoint. Confirm
after the backend lands that the response shape (`evidence` as a nested object, not a string)
matches what `AlertDetail.jsx` expects; adjust the component, not the API client, if it doesn't.

## 5. Data contract (illustrative)

```jsonc
// GET /api/v1/applications/app-1355
{
  "applicationId": "app-1355",
  "machineOutcome": "HIT",
  "finalOutcome": "HIT",
  "processingStatus": "COMPLETE",
  "callbackStatus": "SENT",
  "reasonCode": "SCR_EXACT_MATCH",
  "configVersion": 1,
  "evidence": {
    "normalisedName": "marek nowak",
    "candidates": [
      { "entryId": "WL-001", "matchedFields": ["fullName", "dateOfBirth"],
        "rule": "exact", "verdict": "hit", "weight": 1.0 }
    ],
    "countryRisk": { "highRisk": false, "countryCode": "PL" },
    "sampling": { "sampled": false, "position": null }
  },
  "createdAt": "2026-07-21T21:40:00Z",
  "updatedAt": "2026-07-21T21:40:03Z"
}
```

```jsonc
// GET /api/v1/applications/app-9999 (unknown id)
{
  "timestamp": "2026-07-29T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "no case for app-9999"
}
```

## 6. Definition of done

- [ ] `SamplingEvidence` added; `MatchEvidence` carries it; `decide()` serialises evidence *after*
      the sampling decision so the two never disagree.
- [ ] `ScreeningRecordDetailView` + `ApplicationService.findOne` + `GET
      /api/v1/applications/{id}` (200 / 404).
- [ ] `ApplicationServiceTest` + `ApplicationControllerTest` cover the new endpoint; `./mvnw test`
      green.
- [ ] `AlertDetail.jsx` built from design-system primitives only; wired into `CasesScreen.jsx` via
      row-click.
- [ ] Manually verified against `docker compose up --build`: submit an application through the
      sidecar, open its case, see the real evidence (not a placeholder).
- [ ] Noted to the team: `CasesScreen`'s search (`searchCases`) still 404s until UC-01's endpoint
      exists — this plan does not fix that, only the detail view it links to.
