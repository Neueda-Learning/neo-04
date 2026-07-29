import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Caption, Card, DataTable, EmptyState, KeyValue, PageHeader, Spinner, Split, Stack, Timeline } from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';

/**
 * uc-02 · Review Alert — the evidence panel behind one answered application: what the matcher
 * compared, every watchlist candidate it weighed, the country-risk check, and whether uc-02 rule 4
 * (sampling) forced the outcome to REVIEW.
 *
 * Fetched fresh via `GET /api/v1/applications/{id}` every time it opens — this screen never
 * re-matches, it only replays what {@code ApplicationService.decide()} already decided and stored
 * (the caption below says so, because that is the auditor-facing fact this screen exists to state).
 */
export default function AlertDetail({ applicationId }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [applicant, setApplicant] = useState(null);
  const [applicantError, setApplicantError] = useState(null);
  const [applicantAttempt, setApplicantAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setError(null);
    api
      .getApplication(applicationId)
      .then((d) => !cancelled && setDetail(d))
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [applicationId]);

  useEffect(() => {
    let cancelled = false;
    setApplicant(null);
    setApplicantError(null);
    api
      .getApplicant(applicationId)
      .then((data) => !cancelled && setApplicant(data))
      .catch((e) => !cancelled && setApplicantError(e.message));
    return () => {
      cancelled = true;
    };
  }, [applicationId, applicantAttempt]);

  if (error) {
    return (
      <EmptyState title="Could not load this case">{error}</EmptyState>
    );
  }

  if (!detail) {
    return (
      <Stack gap={4} style={{ alignItems: 'center', padding: 'var(--ds-space-8) 0' }}>
        <Spinner />
      </Stack>
    );
  }

  const evidence = detail.evidence ?? {};
  const candidates = evidence.candidates ?? [];
  const countryRisk = evidence.countryRisk;
  const sampling = evidence.sampling;
  const overrides = detail.overrides ?? [];

  const candidateColumns = [
    { key: 'entryId', header: 'Watchlist entry', mono: true },
    {
      key: 'matchedFields',
      header: 'Matched on',
      render: (c) => (c.matchedFields ?? []).join(', ') || '—',
    },
    { key: 'rule', header: 'Rule' },
    {
      key: 'verdict',
      header: 'Verdict',
      tight: true,
      render: (c) => <Badge tone={statusTone(String(c.verdict).toUpperCase())}>{c.verdict}</Badge>,
    },
    {
      key: 'weight',
      header: 'Similarity',
      numeric: true,
      render: (c) => (typeof c.weight === 'number' ? c.weight.toFixed(2) : '—'),
    },
  ];

  return (
    <Stack gap={6}>
      <PageHeader
        title={detail.applicationId}
        badge={<Badge tone={statusTone(detail.finalOutcome)}>{detail.finalOutcome}</Badge>}
        lede={
          overrides.length
            ? `machine said ${detail.machineOutcome} · operator set ${detail.finalOutcome}`
            : detail.resolution
              ? `${detail.resolution.toLowerCase()} by ${detail.resolvedBy}`
              : detail.finalOutcome !== detail.machineOutcome
                ? `machine said ${detail.machineOutcome} · sampled for review`
                : undefined
        }
        meta={`config v${detail.configVersion ?? '—'} · decided ${time(detail.updatedAt)}`}
      />

      <Split
        sidebar={
          <Stack gap={4}>
            <Card title="Applicant" subtitle="Live from the orchestrator">
              {applicant ? (
                <KeyValue
                  items={[
                    ['Full name', applicant.fullName ?? '—'],
                    ['Date of birth', applicant.dateOfBirth ?? '—'],
                    ['Residence', applicant.countryOfResidence ?? '—'],
                    ['Nationality', applicant.nationality ?? '—'],
                    ['Channel', applicant.channel ?? '—'],
                  ]}
                />
              ) : applicantError ? (
                <Alert
                  title="Applicant details unavailable"
                  action={
                    <Button size="sm" onClick={() => setApplicantAttempt((attempt) => attempt + 1)}>
                      Retry
                    </Button>
                  }
                >
                  {applicantError}
                </Alert>
              ) : (
                <Stack gap={3} style={{ alignItems: 'center', padding: 'var(--ds-space-5) 0' }}>
                  <Spinner />
                  <Caption>Loading applicant details</Caption>
                </Stack>
              )}
            </Card>
            {detail.resolution && (
              <Card title="Human resolution" tone="positive">
                <KeyValue
                  items={[
                    ['Resolution', <Badge tone={statusTone(detail.finalOutcome)}>{detail.resolution}</Badge>],
                    ['Analyst', detail.resolvedBy],
                    ['Resolved', time(detail.resolvedAt)],
                    ['Reason', detail.resolutionReason],
                  ]}
                />
              </Card>
            )}
            <Card title="This case">
              <KeyValue
                items={[
                  ['Application', <span style={{ fontFamily: 'var(--ds-font-mono)' }}>{detail.applicationId}</span>],
                  [
                    'Reason code',
                    <span style={{ fontFamily: 'var(--ds-font-mono)', overflowWrap: 'anywhere' }}>
                      {detail.reasonCode ?? '—'}
                    </span>,
                  ],
                  ['Processing', detail.processingStatus],
                  ['Callback', <Badge tone={statusTone(detail.callbackStatus)}>{detail.callbackStatus}</Badge>],
                  ['Opened', time(detail.createdAt)],
                  ['Decided', time(detail.updatedAt)],
                ]}
              />
            </Card>
            <Caption>
              Evidence replayed from the stored record via GET /api/v1/applications/{'{id}'} —
              this screen never re-matches on open.
            </Caption>
            {overrides.length > 0 && (
              <Card title="Override history" subtitle="append-only audit trail" tone="warning">
                <Timeline
                  items={overrides.map((entry, index) => ({
                    id: `${entry.overriddenAt}-${index}`,
                    title: `${entry.oldOutcome} to ${entry.newOutcome}`,
                    detail: `${entry.operator} · ${entry.reason}`,
                    when: time(entry.overriddenAt),
                    tone: statusTone(entry.newOutcome),
                  }))}
                />
              </Card>
            )}
          </Stack>
        }
      >
        <Stack gap={4}>
          <Card title="Watchlist candidates considered" subtitle={evidence.normalisedName ? `compared as "${evidence.normalisedName}"` : undefined}>
            <DataTable
              columns={candidateColumns}
              rows={candidates}
              rowKey={(c, i) => c.entryId ?? i}
              maxRows={null}
              empty={<EmptyState title="No watchlist candidate came close enough to record">Nothing here — the applicant matched no rule 1–3 candidate.</EmptyState>}
            />
          </Card>

          <Card title="Country risk" tone={countryRisk?.highRisk ? 'warning' : undefined}>
            {countryRisk ? (
              <KeyValue
                items={[
                  ['Country', countryRisk.countryCode ?? '—'],
                  ['High risk', countryRisk.highRisk ? 'Yes' : 'No'],
                ]}
              />
            ) : (
              <Caption>No country of residence was supplied to check.</Caption>
            )}
          </Card>

          <Card title="Sampling (uc-02 rule 4)" tone={sampling?.sampled ? 'warning' : undefined}>
            {sampling ? (
              <KeyValue
                items={[
                  ['Sampled for mandatory review', sampling.sampled ? 'Yes' : 'No'],
                  ['Sample position', sampling.position ?? '—'],
                ]}
              />
            ) : (
              <Caption>This decision predates sampling evidence being recorded.</Caption>
            )}
          </Card>
        </Stack>
      </Split>
    </Stack>
  );
}
