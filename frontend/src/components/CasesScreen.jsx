import React, { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { statusTone, time } from '../status.js';
import { api } from '../api.js';

export default function CasesScreen() {
  const [keyword, setKeyword] = useState('');
  const [rows, setRows] = useState([]);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  const columns = useMemo(
    () => [
      { key: 'applicationId', header: 'Application', mono: true },
      {
        key: 'finalOutcome',
        header: 'Final outcome',
        tight: true,
        render: (r) => <Badge tone={statusTone(r.finalOutcome)}>{r.finalOutcome}</Badge>,
      },
      {
        key: 'processingStatus',
        header: 'Processing',
        tight: true,
        render: (r) => <Badge tone={statusTone(r.processingStatus)}>{r.processingStatus}</Badge>,
      },
      { key: 'createdAt', header: 'Submitted', render: (r) => time(r.createdAt) },
    ],
    []
  );

  async function search() {
    const trimmed = keyword.trim();
    if (!trimmed) {
      setRows([]);
      setMessage(null);
      setError(null);
      setSearched(false);
      return;
    }

    setLoading(true);
    try {
      const result = await api.searchCases(trimmed);
      setRows(Array.isArray(result?.items) ? result.items : []);
      setMessage(result?.message ?? null);
      setError(null);
      setSearched(true);
    } catch (e) {
      setRows([]);
      setMessage(null);
      setError(e.message);
      setSearched(true);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Cases"
        lede="keyword search filters cases, not queried by default; returns up to 10 results, sorted by submission time in descending order"
      />

      {error && (
        <Alert tone="negative" title="failed to retrieve cases">
          {error}
        </Alert>
      )}

      {message && (
        <Alert tone="warning" title="search notice">
          {message}
        </Alert>
      )}

      <Toolbar>
        <SearchInput
          grow
          placeholder="enter keyword (e.g., Application ID)"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              search();
            }
          }}
          aria-label="Search cases"
        />
        <Button onClick={search} disabled={loading}>
          {loading ? 'searching...' : 'search'}
        </Button>
      </Toolbar>

      <DataTable
        columns={columns}
        rows={rows}
        total={rows.length}
        rowKey={(r) => r.applicationId}
        maxRows={null}
        footnote="submitted desc"
        empty={
          <EmptyState title={searched ? 'no matching data' : 'please enter a keyword to search'}>
            {searched ? 'try a different keyword.' : 'the board is empty by default; no data will be queried without search criteria.'}
          </EmptyState>
        }
      />
    </>
  );
}
