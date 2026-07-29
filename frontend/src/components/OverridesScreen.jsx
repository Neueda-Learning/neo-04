import React, { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  ChipGroup,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  Grid,
  MetricTile,
  Modal,
  PageHeader,
  SearchInput,
  Stack,
  Textarea,
  TextInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';
import AlertDetail from './AlertDetail.jsx';

const OUTCOMES = ['CLEAR', 'REVIEW', 'HIT'];

export default function OverridesScreen({ requests, error, onCaseChanged }) {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(null);

  const eligible = useMemo(
    () => requests.filter((row) => row.processingStatus === 'COMPLETE'),
    [requests]
  );
  const rows = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return eligible
      .filter((row) => !needle || row.applicationId.toLowerCase().includes(needle))
      .slice(0, 10);
  }, [eligible, query]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'machineOutcome',
      header: 'Machine',
      render: (row) => <Badge tone={statusTone(row.machineOutcome)}>{row.machineOutcome}</Badge>,
    },
    {
      key: 'finalOutcome',
      header: 'Current',
      render: (row) => <Badge tone={statusTone(row.finalOutcome)}>{row.finalOutcome}</Badge>,
    },
    { key: 'updatedAt', header: 'Last changed', render: (row) => time(row.updatedAt) },
  ];

  return (
    <>
      <PageHeader
        title="Overrides"
        lede="correct an effective outcome with a permanent operator reason"
      />

      {error && <Alert tone="negative" title="Could not load cases">{error}</Alert>}

      <Grid cols={3} min={150} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Eligible cases" value={eligible.length} />
        <MetricTile label="Showing" value={rows.length} tone="info" />
        <MetricTile label="Audit policy" value="Required" tone="warning" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Search override cases"
        />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={rows}
        total={rows.length}
        rowKey={(row) => row.applicationId}
        onRowClick={setSelected}
        footnote="newest first · capped at 10"
        empty={
          <EmptyState title={eligible.length ? 'No case matches that id' : 'No completed cases'}>
            {eligible.length ? 'Clear the search to see recent cases.' : 'Completed screening cases will appear here.'}
          </EmptyState>
        }
      />

      <Modal open={selected != null} title="Override outcome" wide onClose={() => setSelected(null)}>
        {selected && (
          <OverrideCasePanel
            item={selected}
            onChanged={async (detail) => {
              setSelected((current) => ({ ...current, ...detail }));
              await onCaseChanged?.();
            }}
          />
        )}
      </Modal>
    </>
  );
}

function OverrideCasePanel({ item, onChanged }) {
  const [current, setCurrent] = useState(item);
  const [newOutcome, setNewOutcome] = useState(item.finalOutcome === 'HIT' ? 'CLEAR' : 'HIT');
  const [operator, setOperator] = useState('b.dimovski');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);
  const [detailVersion, setDetailVersion] = useState(0);

  const submit = async () => {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      const detail = await api.overrideCase(current.applicationId, {
        newOutcome,
        operator: operator.trim(),
        reason: reason.trim(),
      });
      setCurrent(detail);
      setReason('');
      setSaved(true);
      setDetailVersion((value) => value + 1);
      await onChanged(detail);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack gap={6}>
      <Card
        title="Operator correction"
        subtitle={`machine ${current.machineOutcome} · current ${current.finalOutcome}`}
        tone="warning"
      >
        <Stack gap={4}>
          {saved && <Alert tone="positive" title="Outcome corrected">The audit entry and callback were recorded.</Alert>}
          {error && <Alert tone="negative" title="Override failed">{error}</Alert>}
          <FormGrid cols={2}>
            <Field label="Operator" required>
              {({ id, invalid, describedBy }) => (
                <TextInput
                  id={id}
                  value={operator}
                  invalid={invalid}
                  aria-describedby={describedBy}
                  onChange={(event) => setOperator(event.target.value)}
                  disabled={busy}
                />
              )}
            </Field>
            <Field label="New outcome" required>
              <ChipGroup options={OUTCOMES} value={newOutcome} onChange={setNewOutcome} />
            </Field>
            <FormGrid.Full>
              <Field label="Reason" required hint="Stored permanently in the case history.">
                {({ id, invalid, describedBy }) => (
                  <Textarea
                    id={id}
                    rows={3}
                    value={reason}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    onChange={(event) => setReason(event.target.value)}
                    disabled={busy}
                  />
                )}
              </Field>
            </FormGrid.Full>
          </FormGrid>
          <FormActions>
            <Button
              variant={newOutcome === 'HIT' ? 'danger' : 'primary'}
              busy={busy}
              busyLabel="Recording override"
              disabled={!operator.trim() || !reason.trim() || newOutcome === current.finalOutcome}
              onClick={submit}
            >
              Record override
            </Button>
          </FormActions>
        </Stack>
      </Card>

      <AlertDetail key={detailVersion} applicationId={current.applicationId} />
    </Stack>
  );
}
