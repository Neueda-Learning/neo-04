import React, { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  Modal,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { statusTone, time } from '../status.js';
import AlertDetail from './AlertDetail.jsx';
import '../styles/alerts-screen.css';

const ALERT_OUTCOMES = ['HIT', 'REVIEW'];
const FILTERS = ['All', ...ALERT_OUTCOMES];

/**
 * Alerts & Risk Review: Cases requiring immediate human attention
 * 
 * Displays all HIT and REVIEW applications with direct access to evidence:
 * - Risk metrics and match details
 * - Country risk assessments
 * - Sampling context
 * 
 * Click any row to open detailed evidence in modal (uc-02)
 */
export default function AlertsScreen({ requests, error }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [openId, setOpenId] = useState(null);

  const alerts = useMemo(
    () => requests.filter((r) => ALERT_OUTCOMES.includes(r.finalOutcome)),
    [requests]
  );

  const counts = useMemo(
    () =>
      alerts.reduce((acc, r) => {
        acc[r.finalOutcome] = (acc[r.finalOutcome] ?? 0) + 1;
        return acc;
      }, {}),
    [alerts]
  );

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return alerts.filter((r) => {
      if (filter !== 'All' && r.finalOutcome !== filter) return false;
      if (!needle) return true;
      return r.applicationId.toLowerCase().includes(needle);
    });
  }, [alerts, query, filter]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'finalOutcome',
      header: 'Outcome',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.finalOutcome)}>{r.finalOutcome}</Badge>,
    },
    { key: 'createdAt', header: 'Answered', render: (r) => time(r.createdAt) },
  ];

  return (
    <>
      <PageHeader
        title="Alert"
        lede="applications this module flagged HIT or REVIEW · click a row for the evidence behind it"
      />

      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Hit" value={counts.HIT ?? 0} tone="negative" />
        <MetricTile label="Review" value={counts.REVIEW ?? 0} tone="warning" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search alerts"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        onRowClick={(r) => setOpenId(r.applicationId)}
        footnote="newest first"
        empty={
          <EmptyState title={alerts.length === 0 ? 'No alerts yet' : 'No alert matches that'}>
            {alerts.length === 0 ? (
              <>Nothing has been flagged HIT or REVIEW — every application so far cleared.</>
            ) : (
              <>Clear the search, or pick a different outcome.</>
            )}
          </EmptyState>
        }
      />

      <Modal open={openId != null} title="Case detail" wide onClose={() => setOpenId(null)}>
        {openId != null && <AlertDetail applicationId={openId} />}
      </Modal>
    </>
  );
}
