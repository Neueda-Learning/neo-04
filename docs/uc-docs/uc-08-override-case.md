# Module 4 · Fraud & AML Screening — UC 08 · Override Case

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 4 · Fraud & AML Screening · category Rule · domain `screening` · command `screen-applicant` · outcomes: CLEAR, REVIEW, HIT
- Use case: 08 · Override Case · track B · prerequisite: after 02 is wired · build shape: DB-write→API→FE · primary screen: Override modal
- Data effect: two writes + one callback
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

As a bank employee I want to override a wrong screening outcome with a reason — a HIT that turned out to be an identity coincidence, a CLEAR that new information contradicts.

## Contract

```
POST /cases/{id}/override
{"newOutcome":"CLEAR",
 "reason":"identity coincidence — passport verified distinct",
 "operator":"b.dimovski"}
→ 200 + updated case
```

## Acceptance criteria

1. POST /cases/{id}/override {newOutcome, reason, operator} → 200; the case's outcome updates immediately.
2. reason and operator are mandatory → 400 without either; newOutcome must be CLEAR, REVIEW or HIT.
3. An override_log row is written: applicationId, old outcome, new outcome, reason, operator, timestamp.
4. The module POSTs a fresh callback with status local-manual and the new outcome — SCR_CLEARED_BY_ANALYST when clearing, SCR_CONFIRMED_BY_ANALYST when confirming.
5. Overriding a HIT to CLEAR shows the new outcome on the board, and the override in the case history.  ⟵ **checkpoint — exact value**
6. The original matchResults and machineOutcome stay untouched — the record shows what the machine found AND what the human decided.
7. Overriding to REVIEW puts the case back in the analyst queue, unclaimed.

## Expected data changes

- **UPDATE screening_record** SET outcome — the only field that may ever change here.
- **INSERT override_log** (old, new, reason, operator, at).
- Callback status local-manual tells the orchestrator a human decided — the journey resumes.
- matchResults JSON + machineOutcome untouched: the evidence is preserved forever.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Sequence — this use case

![Sequence — this use case](diagrams/uc-08-sequence.jpg)

<details><summary>mermaid source</summary>

```mermaid
sequenceDiagram
    autonumber
    participant UI
    participant Controller
    participant Service
    participant MySQL
    participant Orchestrator
    UI->>Controller: POST /cases/{id}/override {…}
    Controller->>Service: override(id, cmd)
    Service->>MySQL: UPDATE outcome + INSERT override_log
    MySQL-->>Service: ok
    Service->>Orchestrator: POST /callbacks — local-manual + new outcome
    Controller-->>UI: 200 OK — updated case
```

</details>

### Entity model (suggested — the shape to beat)

![Entity model](diagrams/er-suggested.jpg)

**ScreeningRecord — one row per decision; applicationId is the only applicant identifier**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | PK | the journey key from the envelope — the ONLY applicant-related column in this schema |
| outcome | enum |  | the final answer: CLEAR, REVIEW or HIT — starts equal to machineOutcome; only an analyst decision or override can change it |
| machineOutcome | enum |  | what rules 1–3 computed before sampling — kept so the analyst always sees the machine's own answer |
| reference | string |  | human-facing alert reference shown on every screen and in the callback, e.g. scr-000342 |
| screeningConfigVersion | int | FK | the ScreeningConfig version (watchlist + country list) this alert was matched against — pinned forever, never re-pointed |
| matchedEntryId | string, nullable |  | the watchlist entry an exact match hit, e.g. WL-001; null when no entry matched |
| matchResults | JSON |  | the evidence: normalisedName as actually compared, candidates[] with matched fields and verdict for EVERY entry considered (near-misses included), countryRisk, sampling |
| claimedBy | string, nullable |  | analyst queue — the analyst who claimed the alert; null when unclaimed or released |
| claimedAt | timestamp, nullable |  | analyst queue — when the alert was claimed |
| resolvedBy | string, nullable |  | who made the human decision (queue clear/confirm or override) |
| resolvedAt | timestamp, nullable |  | when the human decision was made |
| resolution | enum, nullable |  | which exit the analyst took: CLEARED or CONFIRMED; null while the alert is open |
| resolutionReason | string, nullable |  | the mandatory reason recorded with every analyst decision |
| submittedAt | timestamp |  | when the orchestrator submitted the case |

**ScreeningConfig — insert-only, versioned lists; the current version is the highest**

| field | type | key | meaning |
|---|---|---|---|
| version | int | PK | one new row per change — rows are inserted, never updated; current = MAX(version) |
| watchlist | JSON |  | array of {entryId, fullName, dateOfBirth, listType}; seeded WL-001 Marek Nowak 1961-04-19 · WL-002 Viktor Orlov 1975-08-22 · WL-003 Amara Diallo 1969-02-10, all SANCTIONS |
| highRiskCountries | JSON |  | ISO alpha-2 codes whose residents park REVIEW under rule 3 (seeded [IR, KP, SY, BY]) |
| sampleEvery | int |  | rule 4's X — every Xth first-time decision parks for an analyst, whatever the rules said (seeded 7) |
| fuzzyThreshold | int, nullable |  | the fuzzy-distance upgrade's dial — no locked rule reads it; locked matching is exact-after-normalisation |
| effectiveFrom | timestamp |  | when this version became the current one |

**OverrideLog — audit trail; one row per manual override, none ever deleted**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | FK | the alert that was overridden |
| oldOutcome | enum |  | the outcome before the override |
| newOutcome | enum |  | the outcome after the override |
| reason | string |  | the mandatory justification typed by the operator |
| operator | string |  | who performed the override |
| overriddenAt | timestamp |  | when it happened |

Relationships: ScreeningRecord N:1 ScreeningConfig — every alert pins the config version it matched against · ScreeningRecord 1:N OverrideLog — every manual override is audited against its alert

<details><summary>mermaid source (generated from the spec tables)</summary>

```mermaid
flowchart LR
    ScreeningRecord["<b>ScreeningRecord</b><br/>————————<br/>applicationId (PK)<br/>outcome<br/>machineOutcome<br/>reference<br/>screeningConfigVersion (FK)<br/>matchedEntryId<br/>matchResults<br/>claimedBy<br/>claimedAt<br/>resolvedBy<br/>resolvedAt<br/>resolution<br/>resolutionReason<br/>submittedAt"]
    ScreeningConfig["<b>ScreeningConfig</b><br/>————————<br/>version (PK)<br/>watchlist<br/>highRiskCountries<br/>sampleEvery<br/>fuzzyThreshold<br/>effectiveFrom"]
    OverrideLog["<b>OverrideLog</b><br/>————————<br/>applicationId (FK)<br/>oldOutcome<br/>newOutcome<br/>reason<br/>operator<br/>overriddenAt"]
    ScreeningRecord -->|"every alert pins the config version it matched against (N:1)"| ScreeningConfig
    ScreeningRecord -->|"every manual override is audited against its alert (1:N)"| OverrideLog
    classDef ent fill:#ffffff,stroke:#2EA98D,color:#22302B
    class ScreeningRecord ent
    class ScreeningConfig ent
    class OverrideLog ent
```

</details>

### State transitions — the case record

![State transitions — the case record](diagrams/case-states.jpg)

<details><summary>mermaid source</summary>

```mermaid
stateDiagram-v2
    direction LR
    [*] --> IN_PROGRESS : /execute accepted (202)
    IN_PROGRESS --> CLEAR : no match, no risk
    IN_PROGRESS --> HIT : exact match (wins)
    IN_PROGRESS --> REVIEW : partial · high-risk country · every 7th sampled
    REVIEW --> CLEAR : analyst clears
    REVIEW --> HIT : analyst confirms
    CLEAR --> HIT : override
    HIT --> CLEAR : override
    HIT --> REVIEW : override (re-opens queue)
    CLEAR --> REVIEW : override (re-opens queue)
    note right of REVIEW
        sampling stores machineOutcome —
        the analyst ALWAYS sees the machine's evidence
        clear/confirm = analyst + mandatory reason
        → resolution trace / override_log
        → callback local-manual, journey resumes
    end note
    classDef ok fill:#ffffff,stroke:#1F8A5D,color:#1F8A5D,font-weight:bold
    classDef warn fill:#ffffff,stroke:#B7791F,color:#B7791F,font-weight:bold
    classDef bad fill:#ffffff,stroke:#B3403A,color:#B3403A,font-weight:bold
    classDef trans fill:#ECF6F1,stroke:#4A635B,color:#22302B
    class CLEAR ok
    class REVIEW warn
    class HIT bad
    class IN_PROGRESS trans
```

</details>

## Out of scope

Deleting or editing any other field of the record; overriding a case that does not exist (404). Working the analyst queue — that is UC 04, with its claim discipline and its two-way resolution.

## Build notes

The ONE permitted mutation outside the queue path. Writes the shared override_log and re-notifies the orchestrator with callback status local-manual, carrying SCR_CLEARED_BY_ANALYST (to CLEAR) or SCR_CONFIRMED_BY_ANALYST (to HIT) so the journey position is corrected by a human on record. Overriding to REVIEW re-opens the alert into the queue.

## Tests

Slice test: happy path, missing reason → 400, unknown id → 404; service test asserts the callback fires once with the right code.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
