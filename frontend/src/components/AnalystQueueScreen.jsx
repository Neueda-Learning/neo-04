import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  Stack,
  Textarea,
  TextInput,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';
import AlertDetail from './AlertDetail.jsx';

const RESOLUTIONS = [
  { value: 'CLEAR', label: 'Clear' },
  { value: 'CONFIRM', label: 'Confirm match' },
];

export default function AnalystQueueScreen({ onCaseChanged }) {
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    try {
      setRows(await api.listReviewQueue());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, 3000);
    return () => clearInterval(id);
  }, [reload]);

  const counts = useMemo(
    () => ({
      open: rows.length,
      unclaimed: rows.filter((row) => !row.claimedBy).length,
      claimed: rows.filter((row) => row.claimedBy).length,
    }),
    [rows]
  );

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'causes',
      header: 'Review cause',
      render: (row) => (
        <div className="queue-causes">
          {(row.causes ?? []).map((cause) => <Badge key={cause} tone="warning">{cause}</Badge>)}
        </div>
      ),
    },
    {
      key: 'claimedBy',
      header: 'Claim',
      render: (row) => row.claimedBy
        ? <Badge tone="info">{row.claimedBy}</Badge>
        : <Badge>Unclaimed</Badge>,
    },
    { key: 'openedAt', header: 'Opened', render: (row) => time(row.openedAt) },
  ];

  const changed = async (next, close = false) => {
    setSelected(close ? null : next);
    await reload();
    onCaseChanged?.();
  };

  return (
    <>
      <PageHeader
        title="Analyst queue"
        lede="open REVIEW alerts · unclaimed first, then oldest · maximum 10"
      />

      {error && <Alert title="Could not load the queue">{error}</Alert>}

      <Grid cols={3} min={150} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Open review" value={counts.open} tone="warning" />
        <MetricTile label="Unclaimed" value={counts.unclaimed} />
        <MetricTile label="Claimed" value={counts.claimed} tone="info" />
      </Grid>

      <DataTable
        columns={columns}
        rows={rows}
        total={rows.length}
        rowKey={(row) => row.applicationId}
        onRowClick={setSelected}
        footnote="unclaimed first · oldest first · capped at 10"
        empty={
          <EmptyState title={loading ? 'Loading queue' : 'Queue clear'}>
            {loading ? 'Checking for open reviews.' : 'There are no open REVIEW alerts.'}
          </EmptyState>
        }
      />

      <Modal open={selected != null} title="Work alert" wide onClose={() => setSelected(null)}>
        {selected && <QueueCasePanel item={selected} onChanged={changed} />}
      </Modal>
    </>
  );
}

function QueueCasePanel({ item, onChanged }) {
  const [current, setCurrent] = useState(item);
  const [analyst, setAnalyst] = useState('r.iqbal');
  const [resolution, setResolution] = useState('CLEAR');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const run = async (action, close = false) => {
    setBusy(true);
    setError(null);
    try {
      const next = await action();
      setCurrent(next);
      await onChanged(next, close);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const mine = current.claimedBy === analyst.trim();
  const claimedByOther = current.claimedBy && !mine;

  return (
    <Stack gap={6}>
      <Card
        title="Analyst decision"
        subtitle={current.claimedBy ? `claimed by ${current.claimedBy}` : 'claim this alert before deciding'}
        tone={claimedByOther ? 'warning' : undefined}
      >
        <Stack gap={4}>
          {error && <Alert title="Action failed">{error}</Alert>}
          <FormGrid cols={2}>
            <Field label="Analyst" required>
              {({ id, invalid, describedBy }) => (
                <TextInput
                  id={id}
                  value={analyst}
                  invalid={invalid}
                  aria-describedby={describedBy}
                  onChange={(event) => setAnalyst(event.target.value)}
                  disabled={busy || Boolean(current.claimedBy)}
                />
              )}
            </Field>
            <Field label="Decision" required>
              <ChipGroup options={RESOLUTIONS} value={resolution} onChange={setResolution} />
            </Field>
            <FormGrid.Full>
              <Field label="Reason" required hint="Recorded permanently with the analyst decision.">
                {({ id, invalid, describedBy }) => (
                  <Textarea
                    id={id}
                    rows={3}
                    value={reason}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    onChange={(event) => setReason(event.target.value)}
                    disabled={!mine || busy}
                  />
                )}
              </Field>
            </FormGrid.Full>
          </FormGrid>
          <FormActions>
            {!current.claimedBy ? (
              <Button
                variant="primary"
                busy={busy}
                busyLabel="Claiming"
                disabled={!analyst.trim()}
                onClick={() => run(() => api.claimCase(current.applicationId, analyst.trim()))}
              >
                Claim alert
              </Button>
            ) : (
              <>
                <Button
                  variant="primary"
                  busy={busy}
                  busyLabel="Recording decision"
                  disabled={!mine || !reason.trim()}
                  onClick={() => run(
                    () => api.resolveCase(current.applicationId, {
                      resolution,
                      reason: reason.trim(),
                      analyst: analyst.trim(),
                    }),
                    true
                  )}
                >
                  Record decision
                </Button>
                <Button
                  disabled={!mine || busy}
                  onClick={() => run(() => api.releaseCase(current.applicationId, analyst.trim()))}
                >
                  Release
                </Button>
              </>
            )}
          </FormActions>
        </Stack>
      </Card>

      <AlertDetail applicationId={current.applicationId} />
    </Stack>
  );
}
