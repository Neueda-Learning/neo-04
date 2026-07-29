import React, { useState, useCallback, useRef } from 'react';
import {
  Alert,
  Badge,
  DataTable,
  EmptyState,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { statusTone, time } from '../status.js';
import { api } from '../api.js';

/**
 * uc-01: Screening Case Board — search by application ID or applicant name.
 *
 * Empty by default (AC1). The applicant-name column hydrates live, one GET per row,
 * cached per page view (AC4). Name column shows "—" if the fetch fails (AC7).
 */
export default function CasesScreen() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null); // null = not searched yet
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  // Cache: applicationId -> applicant name (or '—' on error)
  const nameCache = useRef({});

  const search = useCallback(async (q) => {
    if (!q || !q.trim()) {
      setResults(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const rows = await api.searchCases(q.trim());
      setResults(rows);
      // Hydrate names for each row (AC4 — at most 10 calls per render)
      rows.forEach((row) => {
        if (nameCache.current[row.applicationId] !== undefined) return;
        nameCache.current[row.applicationId] = null; // loading sentinel
        api.getApplication(row.applicationId)
          .then((app) => {
            nameCache.current[row.applicationId] = app?.application?.applicant?.fullName ?? '—';
            // Trigger a re-render by replacing the array
            setResults((prev) => prev ? [...prev] : prev);
          })
          .catch(() => {
            nameCache.current[row.applicationId] = '—';
            setResults((prev) => prev ? [...prev] : prev);
          });
      });
    } catch (e) {
      setError(e.message);
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') search(query);
  };

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'applicantName',
      header: 'Applicant',
      render: (r) => {
        const name = nameCache.current[r.applicationId];
        if (name === undefined || name === null) return <span style={{ color: 'var(--ds-color-text-subtle)' }}>loading…</span>;
        if (name === '—') return <span style={{ color: 'var(--ds-color-text-subtle)' }}>—</span>;
        return name;
      },
    },
    {
      key: 'outcome',
      header: 'Outcome',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.outcome)}>{r.outcome}</Badge>,
    },
    {
      key: 'matchCount',
      header: 'Matches',
      tight: true,
      render: (r) => r.matchCount,
    },
    {
      key: 'submittedAt',
      header: 'Received',
      render: (r) => time(r.submittedAt),
    },
  ];

  return (
    <>
      <PageHeader
        title="Cases"
        lede="search by application ID or applicant name · at most 10 results · empty by default"
      />

      {error && (
        <Alert tone="negative" title="Search failed">
          {error}
        </Alert>
      )}

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application ID or applicant name — press Enter to search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          aria-label="Search cases"
        />
      </Toolbar>

      {results !== null && (
        <DataTable
          columns={columns}
          rows={results}
          total={results.length}
          rowKey={(r) => r.applicationId}
          footnote={results.length === 10 ? 'showing 10 results — refine your search for more' : 'newest first'}
          empty={
            <EmptyState title="No cases match that search">
              Try a different ID or name.
            </EmptyState>
          }
        />
      )}

      {results === null && !loading && (
        <EmptyState title="Enter a search above">
          Type an application ID (e.g. SIM-15) or an applicant name, then press Enter.
        </EmptyState>
      )}
    </>
  );
}
