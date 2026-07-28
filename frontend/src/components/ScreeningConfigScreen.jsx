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
  Section,
  Select,
  Split,
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

  const [preview, setPreview] = useState(null);
  const [busyVersion, setBusyVersion] = useState(null);

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
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
    } finally {
      setBusyVersion(null);
    }
  }

  async function remove(version) {
    if (!window.confirm(`Delete version ${version}? This cannot be undone.`)) return;
    setBusyVersion(version);
    try {
      await api.deleteScreeningConfig(version);
      await refreshList();
      setNotice({ tone: 'positive', text: `Version ${version} deleted.` });
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
    } finally {
      setBusyVersion(null);
    }
  }

  async function openPreview(version) {
    try {
      setPreview(await api.getScreeningConfigVersion(version));
    } catch (e) {
      setNotice({ tone: 'negative', text: e.message });
    }
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
      render: (r) => (r.currentVersion ? <Badge tone="positive">Current</Badge> : <Tag>superseded</Tag>),
    },
    { key: 'samplingFrequency', header: 'Sample every', numeric: true },
    { key: 'createdBy', header: 'Published by' },
    { key: 'createdAt', header: 'Published at', render: (r) => dateTime(r.createdAt) },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <div style={{ display: 'flex', gap: 'var(--ds-space-2)' }}>
          <Button variant="ghost" size="sm" onClick={() => openPreview(r.version)}>
            View
          </Button>
          {!r.currentVersion && (
            <>
              <Button
                variant="secondary"
                size="sm"
                busy={busyVersion === r.version}
                onClick={() => activate(r.version)}
              >
                Activate
              </Button>
              <Button
                variant="danger"
                size="sm"
                busy={busyVersion === r.version}
                onClick={() => remove(r.version)}
              >
                Delete
              </Button>
            </>
          )}
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

      <Split
        ratio="wide-main"
        sidebar={
          <Card title="Version history" subtitle={`${configs.length} version${configs.length === 1 ? '' : 's'}`}>
            <DataTable
              columns={historyColumns}
              rows={configs}
              rowKey={(r) => r.version}
              maxRows={null}
              footnote="oldest supersedable, current always shown"
              empty={<EmptyState title="No versions yet">Publish the first version from the panel on the left.</EmptyState>}
            />
            <Caption>Configuration is insert-only — nothing here is ever updated, only superseded.</Caption>
          </Card>
        }
      >
        <Card
          title="Watchlist entries"
          subtitle={`${draftWatchlist.length} in this draft`}
          headEnd={
            <Button variant="secondary" size="sm" onClick={() => setWatchlistModalOpen(true)}>
              Add entry
            </Button>
          }
        >
          <DataTable
            columns={watchlistColumns}
            rows={draftWatchlist}
            rowKey={(r) => r.key}
            maxRows={null}
            empty={<EmptyState title="No watchlist entries">Add one, then publish a new version.</EmptyState>}
          />
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
            rows={draftCountries}
            rowKey={(r) => r.key}
            maxRows={null}
            empty={<EmptyState title="No country risk entries">Add one, then publish a new version.</EmptyState>}
          />
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
      </Split>

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

      <Modal
        open={!!preview}
        title={preview ? `Version ${preview.version}` : ''}
        onClose={() => setPreview(null)}
        wide
        footer={
          <Button variant="ghost" onClick={() => setPreview(null)}>
            Close
          </Button>
        }
      >
        {preview && (
          <>
            <Caption>
              Published {dateTime(preview.createdAt)} by {preview.createdBy} · sample every{' '}
              {preview.samplingFrequency} · {preview.currentVersion ? 'current version' : 'superseded'}
            </Caption>
            <Section title={`Watchlist (${preview.watchlistEntries.length})`} />
            <DataTable
              columns={watchlistColumns.slice(0, -1)}
              rows={preview.watchlistEntries.map((e, i) => ({ ...e, key: `pw-${i}` }))}
              rowKey={(r) => r.key}
              maxRows={null}
              empty={<EmptyState title="No watchlist entries in this version" />}
            />
            <Section title={`Country risk (${preview.countryRiskEntries.length})`} style={{ marginTop: 'var(--ds-space-6)' }} />
            <DataTable
              columns={countryColumns.slice(0, -1)}
              rows={preview.countryRiskEntries.map((e, i) => ({ ...e, key: `pc-${i}` }))}
              rowKey={(r) => r.key}
              maxRows={null}
              empty={<EmptyState title="No country risk entries in this version" />}
            />
          </>
        )}
      </Modal>
    </>
  );
}
