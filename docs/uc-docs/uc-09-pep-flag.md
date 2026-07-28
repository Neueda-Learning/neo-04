# Module 4 · Fraud & AML Screening — UC 09 · PEP flag (CANDIDATE)

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 4 · Fraud & AML Screening · category Rule · domain `screening` · command `screen-applicant` · outcomes: CLEAR, REVIEW, HIT
- Use case: 09 · PEP flag · track C · prerequisite: after 01–08 · build shape: config field + engine branch · primary screen: Alert Detail (list types)
- Data effect: watchlist field + engine branch
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

Not everyone on a list is banned. A politically exposed person — a minister, a judge, their close family — may absolutely become a customer, but only after enhanced due diligence. The list mechanics are identical to sanctions; the OUTCOME is not: a PEP exact match is a REVIEW for a person, never an automatic HIT.

## What it adds

- The watchlist's `listType` gains a second value in anger: PEP alongside SANCTIONS (the field already exists — this candidate makes it matter).
- The matcher branches on listType at the exact-match step: SANCTIONS exact → HIT as today; PEP exact → REVIEW; PEP partial → REVIEW as today.
- New reason code `SCR_PEP_MATCH` — a v5 contract addition, instructor applies it.
- Seed change: one PEP entry added (e.g. a fictional former minister), so the demo has a case.

## Acceptance criteria

1. An applicant exactly matching a PEP entry (name + DOB) → REVIEW with SCR_PEP_MATCH — parked for enhanced due diligence, not rejected.
2. An applicant exactly matching a SANCTIONS entry is still HIT — all eight UC 01–08 ACs pass unchanged.
3. An applicant matching BOTH a PEP and a SANCTIONS entry → HIT; sanctions outrank PEP.
4. The analyst queue badges PEP alerts distinctly, and the evidence panel names the list type per candidate.
5. The PEP entry is versioned config like any other — added or removed without a deploy, old alerts pinned.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

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

## Why this one

Cheapest candidate on the module's list: the listType field already exists, so the whole cost is one matcher branch and one badge — and it teaches the most misunderstood fact in screening: a match is not a verdict; the LIST decides what a match means.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
