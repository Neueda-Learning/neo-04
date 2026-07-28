import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Caption,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  Modal,
  PageHeader,
  Select,
  Tag,
  TextInput,
} from '../design-system';
import { api } from '../api.js';
import { dateTime, riskTone, RISK_LEVELS } from '../screeningConfig.js';

const blankWatchlist = () => ({
  listId: '',
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  nationality: '',
  listType: 'SANCTIONS',
  source: '',
});

const blankCountry = () => ({ countryCode: '', countryName: '', riskLevel: 'HIGH' });

function toDraftWatchlist(entries = []) {
  return entries.map((e, i) => ({
    key: `w-${e.id ?? i}`,
    listId: e.listId ?? '',
    firstName: e.firstName ?? '',
    lastName: e.lastName ?? '',
    dateOfBirth: e.dateOfBirth ?? '',
    nationality: e.nationality ?? '',
    listType: e.listType ?? '',
    source: e.source ?? '',
  }));
}

function toDraftCountries(entries = []) {
  return entries.map((e, i) => ({
    key: `c-${e.id ?? i}`,
    countryCode: e.countryCode ?? '',
    countryName: e.countryName ?? '',
    riskLevel: e.riskLevel ?? 'HIGH',
  }));
}

/**
 * Screening configuration — the watchlist and the high-risk country list every screening
 * decision is matched against (UC 05 · UC 06 · UC 07). One document per version, insert-only:
 * "editing" builds a new version from the current one and publishes it; nothing is ever
 * updated or deleted in place. History is the audit trail, so it is always on screen beside
 * the current values (design-system/DESIGN.md § "Config").
 */
export default function ScreeningConfigScreen() {
  const [configs, setConfigs] = useState([]);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const [draftWatchlist, setDraftWatchlist] = useState([]);
  const [draftCountries, setDraftCountries] = useState([]);
  const [samplingFrequency, setSamplingFrequency] = useState(7);
  const [operator, setOperator] = useState('');
  const [activateOnPublish, setActivateOnPublish] = useState(true);
  const [publishing, setPublishing] = useState(false);

  const [watchlistModalOpen, setWatchlistModalOpen] = useState(false);
  const [newWatchlist, setNewWatchlist] = useState(blankWatchlist());
  const [countryModalOpen, setCountryModalOpen] = useState(false);
  const [newCountry, setNewCountry] = useState(blankCountry());

  const [busyVersion, setBusyVersion] = useState(null);
  const [historyPage, setHistoryPage] = useState(1);
  const HISTORY_PAGE_SIZE = 10;
  const [watchlistPage, setWatchlistPage] = useState(1);
  const WATCHLIST_PAGE_SIZE = 10;
  const [countryPage, setCountryPage] = useState(1);
  const COUNTRY_PAGE_SIZE = 5;

  const [mode, setMode] = useState('history'); // 'history' | 'edit' | 'view'
  const [viewingVersion, setViewingVersion] = useState(null);
  const [viewLoading, setViewLoading] = useState(false);
  const [loadingVersion, setLoadingVersion] = useState(null);

  const resetDraftFrom = useCallback((detail) => {
    setDraftWatchlist(toDraftWatchlist(detail?.watchlistEntries));
    setDraftCountries(toDraftCountries(detail?.countryRiskEntries));
    setSamplingFrequency(detail?.samplingFrequency ?? 7);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, detail] = await Promise.all([
        api.listScreeningConfigs(),
        api.getCurrentScreeningConfig().catch((e) => {
          if (e.status === 404) return null;
          throw e;
        }),
      ]);
      setConfigs(list);
      setCurrent(detail);
      resetDraftFrom(detail);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [resetDraftFrom]);

  useEffect(() => {
    load();
  }, [load]);

  const dirty = useMemo(() => {
    const baseline = JSON.stringify({
      samplingFrequency: current?.samplingFrequency ?? 7,
      w: toDraftWatchlist(current?.watchlistEntries),
      c: toDraftCountries(current?.countryRiskEntries),
    });
    const draft = JSON.stringify({
      samplingFrequency: Number(samplingFrequency),
      w: draftWatchlist,
      c: draftCountries,
    });
    return baseline !== draft;
  }, [current, samplingFrequency, draftWatchlist, draftCountries]);

  const supersededConfigs = useMemo(() => configs.filter((c) => !c.currentVersion), [configs]);

  const historyTotalPages = Math.max(1, Math.ceil(supersededConfigs.length / HISTORY_PAGE_SIZE));

  useEffect(() => {
    setHistoryPage((p) => Math.min(p, historyTotalPages));
  }, [historyTotalPages]);

  const historyRows = useMemo(
    () => supersededConfigs.slice((historyPage - 1) * HISTORY_PAGE_SIZE, historyPage * HISTORY_PAGE_SIZE),
    [supersededConfigs, historyPage]
  );

  const watchlistTotalPages = Math.max(1, Math.ceil(draftWatchlist.length / WATCHLIST_PAGE_SIZE));

  useEffect(() => {
    setWatchlistPage((p) => Math.min(p, watchlistTotalPages));
  }, [watchlistTotalPages]);

  const watchlistRows = useMemo(
    () => draftWatchlist.slice((watchlistPage - 1) * WATCHLIST_PAGE_SIZE, watchlistPage * WATCHLIST_PAGE_SIZE),
    [draftWatchlist, watchlistPage]
  );

  const countryTotalPages = Math.max(1, Math.ceil(draftCountries.length / COUNTRY_PAGE_SIZE));

  useEffect(() => {
    setCountryPage((p) => Math.min(p, countryTotalPages));
  }, [countryTotalPages]);

  const countryRows = useMemo(
    () => draftCountries.slice((countryPage - 1) * COUNTRY_PAGE_SIZE, countryPage * COUNTRY_PAGE_SIZE),
    [draftCountries, countryPage]
  );

  async function refreshList() {
    setConfigs(await api.listScreeningConfigs());
  }

  async function publish() {
    if (!operator.trim()) {
      setNotice({ tone: 'negative', text: 'Enter your name — createdBy is mandatory.' });
      return;
    }
    if (!Number.isInteger(Number(samplingFrequency)) || Number(samplingFrequency) <= 0) {
      setNotice({ tone: 'negative', text: 'Sample-every must be a positive whole number.' });
      return;
    }
    setPublishing(true);
    setNotice(null);
    try {
      const created = await api.createScreeningConfig({
        samplingFrequency: Number(samplingFrequency),
        createdBy: operator.trim(),
        activate: activateOnPublish,
        watchlistEntries: draftWatchlist.map(({ key, ...rest }) => rest),
        countryRiskEntries: draftCountries.map(({ key, ...rest }) => rest),
      });
      setNotice({
        tone: 'positive',
        text: `Version ${created.version} published${activateOnPublish ? ' and activated' : ''}.`,
      });
      await refreshList();
      if (activateOnPublish) {
        setCurrent(created);
        resetDraftFrom(created);
      }
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
    } finally {
      setPublishing(false);
    }
  }

  async function activate(version) {
    setBusyVersion(version);
    try {
      const detail = await api.activateScreeningConfig(version);
      setCurrent(detail);
      resetDraftFrom(detail);
      await refreshList();
      setNotice({ tone: 'positive', text: `Version ${version} is now current.` });
      return detail;
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
      return null;
    } finally {
      setBusyVersion(null);
    }
  }

  function editCurrent() {
    setMode('edit');
  }

  function backToHistory() {
    setMode('history');
    setViewingVersion(null);
  }

  async function openVersion(version) {
    setLoadingVersion(version);
    setViewLoading(true);
    try {
      setViewingVersion(await api.getScreeningConfigVersion(version));
      setMode('view');
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
    } finally {
      setViewLoading(false);
      setLoadingVersion(null);
    }
  }

  async function activateAndEdit(version) {
    const detail = await activate(version);
    if (detail) setMode('edit');
  }

  function addWatchlistEntry() {
    if (!newWatchlist.listId.trim()) return;
    setDraftWatchlist((prev) => [
      ...prev,
      { ...newWatchlist, key: `w-new-${Date.now()}`, listId: newWatchlist.listId.trim() },
    ]);
    setNewWatchlist(blankWatchlist());
    setWatchlistModalOpen(false);
  }

  function addCountryEntry() {
    const code = newCountry.countryCode.trim().toUpperCase();
    if (!code || !newCountry.countryName.trim()) return;
    setDraftCountries((prev) => [
      ...prev,
      { ...newCountry, key: `c-new-${Date.now()}`, countryCode: code, countryName: newCountry.countryName.trim() },
    ]);
    setNewCountry(blankCountry());
    setCountryModalOpen(false);
  }

  const watchlistColumns = [
    { key: 'listId', header: 'Entry', mono: true, render: (r) => <Tag>{r.listId}</Tag> },
    { key: 'name', header: 'Name', render: (r) => `${r.firstName} ${r.lastName}`.trim() || '—' },
    { key: 'dateOfBirth', header: 'Date of birth', render: (r) => r.dateOfBirth || '—' },
    { key: 'nationality', header: 'Nationality', tight: true, render: (r) => r.nationality || '—' },
    { key: 'listType', header: 'List type', tight: true, render: (r) => <Tag>{r.listType || '—'}</Tag> },
    { key: 'source', header: 'Source', render: (r) => r.source || '—' },
    {
      key: 'actions',
      header: '',
      tight: true,
      render: (r) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setDraftWatchlist((prev) => prev.filter((e) => e.key !== r.key))}
        >
          Remove
        </Button>
      ),
    },
  ];

  const countryColumns = [
    { key: 'countryCode', header: 'Country', mono: true, render: (r) => <Tag>{r.countryCode}</Tag> },
    { key: 'countryName', header: 'Name' },
    {
      key: 'riskLevel',
      header: 'Risk',
      tight: true,
      render: (r) => <Badge tone={riskTone(r.riskLevel)}>{r.riskLevel}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      tight: true,
      render: (r) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setDraftCountries((prev) => prev.filter((e) => e.key !== r.key))}
        >
          Remove
        </Button>
      ),
    },
  ];

  const historyColumns = [
    { key: 'version', header: 'Version', mono: true, tight: true },
    {
      key: 'currentVersion',
      header: 'Status',
      tight: true,
      render: () => <Tag>superseded</Tag>,
    },
    { key: 'samplingFrequency', header: 'Sample every' },
    { key: 'createdBy', header: 'Published by' },
    { key: 'createdAt', header: 'Published at', render: (r) => dateTime(r.createdAt) },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <div style={{ display: 'flex', gap: 'var(--ds-space-2)' }}>
          <Button
            variant="ghost"
            size="sm"
            busy={viewLoading && loadingVersion === r.version}
            onClick={() => openVersion(r.version)}
          >
            View
          </Button>
          <Button variant="secondary" size="sm" busy={busyVersion === r.version} onClick={() => activate(r.version)}>
            Activate
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Screening configuration"
        lede="the watchlist and high-risk country list every screening decision is matched against — insert-only, one document per version"
        meta={
          current
            ? `v${current.version} current · published ${dateTime(current.createdAt)} by ${current.createdBy}`
            : 'no version published yet'
        }
      />

      {error && (
        <Alert tone="negative" title="Could not load screening configuration">
          {error}
        </Alert>
      )}

      {notice && (
        <Alert tone={notice.tone} title={notice.tone === 'negative' ? 'Something went wrong' : 'Done'}>
          {notice.text}
        </Alert>
      )}

      {mode === 'history' && (
        <Card
          title="Version history"
          subtitle={`${configs.length} version${configs.length === 1 ? '' : 's'}`}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: 'var(--ds-space-4)',
              marginBottom: 'var(--ds-space-4)',
              border: '1px solid var(--ds-color-border)',
              borderRadius: 'var(--ds-radius-md)',
            }}
          >
            {current ? (
              <>
                <div>
                  <div style={{ display: 'flex', gap: 'var(--ds-space-2)', alignItems: 'center' }}>
                    <Badge tone="positive">Current</Badge>
                    <Tag mono>Version {current.version}</Tag>
                  </div>
                  <Caption>
                    Sample every {current.samplingFrequency} · published {dateTime(current.createdAt)} by{' '}
                    {current.createdBy}
                  </Caption>
                </div>
                <Button variant="primary" size="sm" onClick={editCurrent}>
                  Edit
                </Button>
              </>
            ) : (
              <>
                <Caption>No version published yet.</Caption>
                <Button variant="primary" size="sm" onClick={editCurrent}>
                  Create first version
                </Button>
              </>
            )}
          </div>

          <DataTable
            columns={historyColumns}
            rows={historyRows}
            rowKey={(r) => r.version}
            maxRows={null}
            footnote="oldest supersedable"
            empty={<EmptyState title="No superseded versions yet">Past versions appear here once you publish a new one.</EmptyState>}
          />
          {historyTotalPages > 1 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 'var(--ds-space-3)',
                marginTop: 'var(--ds-space-2)',
              }}
            >
              <Button
                variant="ghost"
                size="sm"
                disabled={historyPage <= 1}
                onClick={() => setHistoryPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </Button>
              <Caption>
                Page {historyPage} of {historyTotalPages}
              </Caption>
              <Button
                variant="ghost"
                size="sm"
                disabled={historyPage >= historyTotalPages}
                onClick={() => setHistoryPage((p) => Math.min(historyTotalPages, p + 1))}
              >
                Next
              </Button>
            </div>
          )}
          <Caption>Configuration is insert-only — nothing here is ever updated, only superseded.</Caption>
        </Card>
      )}

      {mode === 'edit' && (
        <div>
          <Button variant="ghost" size="sm" onClick={backToHistory}>
            ← Back to version history
          </Button>

        <Card
          title="Watchlist entries"
          subtitle={`${draftWatchlist.length} in this draft`}
          headEnd={
            <Button variant="secondary" size="sm" onClick={() => setWatchlistModalOpen(true)}>
              Add entry
            </Button>
          }
          style={{ marginTop: 'var(--ds-space-4)' }}
        >
          <DataTable
            columns={watchlistColumns}
            rows={watchlistRows}
            rowKey={(r) => r.key}
            maxRows={null}
            empty={<EmptyState title="No watchlist entries">Add one, then publish a new version.</EmptyState>}
          />
          {watchlistTotalPages > 1 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 'var(--ds-space-3)',
                marginTop: 'var(--ds-space-2)',
              }}
            >
              <Button
                variant="ghost"
                size="sm"
                disabled={watchlistPage <= 1}
                onClick={() => setWatchlistPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </Button>
              <Caption>
                Page {watchlistPage} of {watchlistTotalPages}
              </Caption>
              <Button
                variant="ghost"
                size="sm"
                disabled={watchlistPage >= watchlistTotalPages}
                onClick={() => setWatchlistPage((p) => Math.min(watchlistTotalPages, p + 1))}
              >
                Next
              </Button>
            </div>
          )}
        </Card>

        <Card
          title="Country risk list"
          subtitle={`${draftCountries.length} in this draft`}
          headEnd={
            <Button variant="secondary" size="sm" onClick={() => setCountryModalOpen(true)}>
              Add entry
            </Button>
          }
          style={{ marginTop: 'var(--ds-space-6)' }}
        >
          <DataTable
            columns={countryColumns}
            rows={countryRows}
            rowKey={(r) => r.key}
            maxRows={null}
            empty={<EmptyState title="No country risk entries">Add one, then publish a new version.</EmptyState>}
          />
          {countryTotalPages > 1 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 'var(--ds-space-3)',
                marginTop: 'var(--ds-space-2)',
              }}
            >
              <Button
                variant="ghost"
                size="sm"
                disabled={countryPage <= 1}
                onClick={() => setCountryPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </Button>
              <Caption>
                Page {countryPage} of {countryTotalPages}
              </Caption>
              <Button
                variant="ghost"
                size="sm"
                disabled={countryPage >= countryTotalPages}
                onClick={() => setCountryPage((p) => Math.min(countryTotalPages, p + 1))}
              >
                Next
              </Button>
            </div>
          )}
        </Card>

        <Card title="Publish new version" style={{ marginTop: 'var(--ds-space-6)' }}>
          <FormGrid cols={3}>
            <Field label="Sample every (Nth decision parked for review)" required>
              {({ id }) => (
                <TextInput
                  id={id}
                  type="number"
                  min={1}
                  value={samplingFrequency}
                  onChange={(e) => setSamplingFrequency(e.target.value)}
                />
              )}
            </Field>
            <Field label="Your name (createdBy)" required>
              {({ id }) => (
                <TextInput
                  id={id}
                  value={operator}
                  onChange={(e) => setOperator(e.target.value)}
                  placeholder="ops-team"
                />
              )}
            </Field>
            <Field label="Activate immediately">
              {({ id }) => (
                <Select
                  id={id}
                  value={activateOnPublish ? 'yes' : 'no'}
                  onChange={(e) => setActivateOnPublish(e.target.value === 'yes')}
                  options={[
                    { value: 'yes', label: 'Yes — make this current' },
                    { value: 'no', label: 'No — keep the current version' },
                  ]}
                />
              )}
            </Field>
          </FormGrid>
          <FormActions>
            <Button variant="primary" busy={publishing} disabled={!dirty} onClick={publish}>
              Publish new version
            </Button>
          </FormActions>
          <Caption>
            {dirty
              ? 'This draft differs from the current version — publishing writes a brand-new version, never an edit.'
              : 'No changes yet — add or remove an entry above to build the next version.'}
          </Caption>
        </Card>
        </div>
      )}

      {mode === 'view' && viewingVersion && (
        <div>
          <Button variant="ghost" size="sm" onClick={backToHistory}>
            ← Back to version history
          </Button>
          <Caption>
            Version {viewingVersion.version} · published {dateTime(viewingVersion.createdAt)} by{' '}
            {viewingVersion.createdBy} · sample every {viewingVersion.samplingFrequency} · superseded (read-only)
          </Caption>

          <Card
            title={`Watchlist entries (${viewingVersion.watchlistEntries.length})`}
            style={{ marginTop: 'var(--ds-space-4)' }}
          >
            <DataTable
              columns={watchlistColumns.slice(0, -1)}
              rows={viewingVersion.watchlistEntries.map((e, i) => ({ ...e, key: `vw-${i}` }))}
              rowKey={(r) => r.key}
              maxRows={null}
              empty={<EmptyState title="No watchlist entries in this version" />}
            />
          </Card>

          <Card
            title={`Country risk list (${viewingVersion.countryRiskEntries.length})`}
            style={{ marginTop: 'var(--ds-space-6)' }}
          >
            <DataTable
              columns={countryColumns.slice(0, -1)}
              rows={viewingVersion.countryRiskEntries.map((e, i) => ({ ...e, key: `vc-${i}` }))}
              rowKey={(r) => r.key}
              maxRows={null}
              empty={<EmptyState title="No country risk entries in this version" />}
            />
          </Card>

          <FormActions>
            <Button
              variant="primary"
              busy={busyVersion === viewingVersion.version}
              onClick={() => activateAndEdit(viewingVersion.version)}
            >
              Activate this version
            </Button>
          </FormActions>
        </div>
      )}

      <Modal
        open={watchlistModalOpen}
        title="Add watchlist entry"
        onClose={() => setWatchlistModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setWatchlistModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={addWatchlistEntry} disabled={!newWatchlist.listId.trim()}>
              Add to draft
            </Button>
          </>
        }
      >
        <FormGrid cols={2}>
          <Field label="Entry id" required>
            {({ id }) => (
              <TextInput
                id={id}
                mono
                value={newWatchlist.listId}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, listId: e.target.value }))}
                placeholder="WL-004"
              />
            )}
          </Field>
          <Field label="List type">
            {({ id }) => (
              <TextInput
                id={id}
                value={newWatchlist.listType}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, listType: e.target.value }))}
                placeholder="SANCTIONS"
              />
            )}
          </Field>
          <Field label="First name">
            {({ id }) => (
              <TextInput
                id={id}
                value={newWatchlist.firstName}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, firstName: e.target.value }))}
              />
            )}
          </Field>
          <Field label="Last name">
            {({ id }) => (
              <TextInput
                id={id}
                value={newWatchlist.lastName}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, lastName: e.target.value }))}
              />
            )}
          </Field>
          <Field label="Date of birth">
            {({ id }) => (
              <TextInput
                id={id}
                type="date"
                value={newWatchlist.dateOfBirth}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, dateOfBirth: e.target.value }))}
              />
            )}
          </Field>
          <Field label="Nationality">
            {({ id }) => (
              <TextInput
                id={id}
                value={newWatchlist.nationality}
                onChange={(e) => setNewWatchlist((s) => ({ ...s, nationality: e.target.value }))}
                placeholder="GB"
              />
            )}
          </Field>
          <FormGrid.Full>
            <Field label="Source">
              {({ id }) => (
                <TextInput
                  id={id}
                  value={newWatchlist.source}
                  onChange={(e) => setNewWatchlist((s) => ({ ...s, source: e.target.value }))}
                  placeholder="OFAC"
                />
              )}
            </Field>
          </FormGrid.Full>
        </FormGrid>
      </Modal>

      <Modal
        open={countryModalOpen}
        title="Add country risk entry"
        onClose={() => setCountryModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCountryModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={addCountryEntry}
              disabled={!newCountry.countryCode.trim() || !newCountry.countryName.trim()}
            >
              Add to draft
            </Button>
          </>
        }
      >
        <FormGrid cols={2}>
          <Field label="Country code (ISO alpha-2)" required>
            {({ id }) => (
              <TextInput
                id={id}
                mono
                maxLength={2}
                value={newCountry.countryCode}
                onChange={(e) => setNewCountry((s) => ({ ...s, countryCode: e.target.value.toUpperCase() }))}
                placeholder="IR"
              />
            )}
          </Field>
          <Field label="Country name" required>
            {({ id }) => (
              <TextInput
                id={id}
                value={newCountry.countryName}
                onChange={(e) => setNewCountry((s) => ({ ...s, countryName: e.target.value }))}
                placeholder="Iran"
              />
            )}
          </Field>
          <FormGrid.Full>
            <Field label="Risk level" required>
              {({ id }) => (
                <Select
                  id={id}
                  value={newCountry.riskLevel}
                  onChange={(e) => setNewCountry((s) => ({ ...s, riskLevel: e.target.value }))}
                  options={RISK_LEVELS}
                />
              )}
            </Field>
          </FormGrid.Full>
        </FormGrid>
      </Modal>
    </>
  );
}
