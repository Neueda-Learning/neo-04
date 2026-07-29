import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import AlertsScreen from './components/AlertsScreen.jsx';
import ScreeningConfigScreen from './components/ScreeningConfigScreen.jsx';
import AnalystQueueScreen from './components/AnalystQueueScreen.jsx';
import OverridesScreen from './components/OverridesScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

function getInitialScreen() {
  const screen = new URLSearchParams(window.location.search).get('screen');
  return ['alerts', 'queue', 'overrides', 'screening-config'].includes(screen)
    ? screen
    : 'applications';
}

function getInitialScreeningConfigMode() {
  return new URLSearchParams(window.location.search).get('screeningConfigMode') === 'edit'
    ? 'edit'
    : 'history';
}

/**
 * The screens in the side menu.
 *
 * ⚠️ One real screen and three placeholders — the placeholders are there so the menu shows you
 * where your own screens go, and they are `disabled` so nobody clicks into nothing. Replace them
 * with what your business topic actually needs; the operator UI is a graded deliverable, and a
 * single read-only list is not one.
 */
const SCREENS = [
  { id: 'applications', label: 'Applications' },
  { id: 'alerts', label: 'Alert', hint: 'HIT · REVIEW' },
  { id: 'queue', label: 'Analyst queue', hint: 'claim · decide' },
  { id: 'overrides', label: 'Overrides', hint: 'operator actions' },
  { id: 'screening-config', label: 'Screening config', hint: 'watchlist · country risk · history' },
];

/**
 * A sidebar rather than a top bar: this app is expected to grow more screens than a row of tabs
 * holds, and the menu is where a team plans that growth. The identity box above it is the only
 * place the app says who it belongs to — its values come from `/info`, so the same image reads
 * "Team 07" once SERVICE_TEAM says so.
 */
export default function App() {
  const [screen, setScreen] = useState(getInitialScreen);
  const [screeningConfigMode, setScreeningConfigMode] = useState(getInitialScreeningConfigMode);
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const refreshHealth = useCallback(async () => {
    try {
      const [h, i] = await Promise.all([api.health(), api.info()]);
      setHealth(h);
      setInfo(i);
    } catch {
      setHealth(null);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  const up = !error && health?.status === 'UP';
  const side =
    screeningConfigMode === 'edit'
      ? null
      : (
        <>
          <SideBrand
            brand={info?.team ?? 'Team'}
            product={info?.service ?? 'Module'}
            meta={info ? `${info.serviceId} · ${info.domain}` : undefined}
          />
          <SideNav
            items={SCREENS}
            active={screen}
            onSelect={(next) => {
              setScreen(next);
              if (next !== 'screening-config') setScreeningConfigMode('history');
            }}
          />
          {/* Health and refresh lived in the top bar; with the bar gone they belong beside the
              menu rather than inside it — a menu item that is not a screen is a trap. */}
          <div className="app-side-status">
            <StatusPill tone={up ? 'positive' : 'negative'}>{up ? 'Up' : 'Down'}</StatusPill>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                reload();
                refreshHealth();
              }}
            >
              Refresh
            </Button>
          </div>
        </>
      );

  return (
    <AppShell
      side={side}
      footer="One of ten modules · applications arrive from the orchestrator, never from this UI"
    >
      {screen === 'applications' && (
        <RequestsScreen requests={requests} error={error} info={info} />
      )}
      {screen === 'alerts' && <AlertsScreen requests={requests} error={error} />}
      {screen === 'queue' && <AnalystQueueScreen onCaseChanged={reload} />}
      {screen === 'overrides' && (
        <OverridesScreen requests={requests} error={error} onCaseChanged={reload} />
      )}
      {screen === 'screening-config' && (
        <ScreeningConfigScreen
          mode={screeningConfigMode}
          onModeChange={(nextMode) => setScreeningConfigMode(nextMode)}
        />
      )}
    </AppShell>
  );
}
