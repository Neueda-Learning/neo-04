import React, { useCallback, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, STATUSES, time } from '../status.js';
import '../styles/requests-screen.css';

const FILTERS = ['All', ...STATUSES];

/**
 * Applications Board: Server-side search for IDs/names, status filtering, and quick metrics.
 * 
 * Features:
 * - Real-time server-side search (debounced 300ms)
 * - Status-based filtering with live counts
 * - Responsive metric cards showing key statistics
 * - Empty states guide users through initial setup
 */
export default function RequestsScreen({ requests, error, info }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [searchResults, setSearchResults] = useState(null);
  const [searching, setSearching] = useState(false);
  const searchTimeoutRef = useRef(null);

  const counts = useMemo(
    () =>
      requests.reduce((acc, r) => {
        acc[r.finalOutcome] = (acc[r.finalOutcome] ?? 0) + 1;
        return acc;
      }, {}),
    [requests]
  );

  // Server-side search: call backend when query changes
  const handleSearch = useCallback((searchQuery) => {
    setQuery(searchQuery);

    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }

    if (!searchQuery.trim()) {
      setSearchResults(null);
      return;
    }

    setSearching(true);
    searchTimeoutRef.current = setTimeout(async () => {
      try {
        const results = await api.searchCases(searchQuery, 10);
        setSearchResults(results || []);
      } catch (err) {
        console.warn('Search failed:', err);
        setSearchResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);
  }, []);

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();

    // If search results exist, use them; otherwise filter local requests
    if (searchResults !== null) {
      return searchResults.filter((r) => {
        if (filter !== 'All' && r.outcome !== filter) return false;
        return true;
      });
    }

    // Local filter when no search
    return requests.filter((r) => {
      if (filter !== 'All' && r.finalOutcome !== filter) return false;
      if (!needle) return true;
      return r.applicationId.toLowerCase().includes(needle);
    });
  }, [requests, query, filter, searchResults]);

  const columns = useMemo(
    () => [
      { key: 'applicationId', header: 'Application', mono: true },
      {
        key: 'finalOutcome',
        header: 'Outcome',
        tight: true,
        render: (r) => <Badge tone={statusTone(r.finalOutcome)}>{r.finalOutcome || r.outcome}</Badge>,
      },
      { key: 'createdAt', header: 'Answered', render: (r) => time(r.createdAt) },
    ],
    []
  );


  return (
    <>
      <PageHeader
        title="Applications"
        lede="everything the orchestrator has sent this module, and what it answered · newest first"
        meta={
          info
            ? `${info.serviceId} · ${info.domain} · v${info.version}` +
              (info.mockedDependencies?.length
                ? ` · mocking ${info.mockedDependencies.join(', ')}`
                : ' · nothing mocked')
            : undefined
        }
      />

      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Seen" value={requests.length} />
        <MetricTile label="Clear" value={counts.CLEAR ?? 0} tone="positive" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Search by ID or name..."
          value={query}
          onChange={(e) => handleSearch(e.target.value)}
          aria-label="Search applications by ID or applicant name"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        footnote="newest first"
        empty={
          <EmptyState
            title={requests.length === 0 ? 'Nothing received yet' : 'No application matches that'}
          >
            {requests.length === 0 ? (
              <>
                Send one from the <strong>sidecar</strong> at <strong>localhost:9000</strong>, or turn
                the generator on in the orchestrator UI. Nothing in this screen sends applications —
                this module is called, it does not call itself.
              </>
            ) : (
              <>Clear the search, or pick a different status.</>
            )}
          </EmptyState>
        }
      />
    </>
  );
}
