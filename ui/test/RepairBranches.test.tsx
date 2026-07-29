import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { BASELINES, DOCUMENTS, DOCUMENT_TYPES, REPAIRERS, SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type Route, installFetchMock } from './mockFetch';

// Secondary branches of the Repair page: the scan form restored from cookies (every field has a
// saved-value branch that a fresh browser never takes), a saved subtype that the loaded type list no
// longer offers, and saved repairer selections/settings. The main scan/repair flow lives in
// Repair.test.tsx.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const COOKIES = [
  'entityType',
  'filterMode',
  'selectedEntities',
  'userQuery',
  'revision',
  'sort',
  'limit',
  'timeout',
  'hideValid',
  'entitySubtype',
  'repairers_WORKITEM',
  'repairers_DOCUMENT',
  `rc_${REPAIRERS[0].id}_${REPAIRERS[0].configs[0]?.key}`,
];

const COOKIE_PREFIX = 'xmlRepair_';
const setCookie = (name: string, value: string) => {
  document.cookie = `${COOKIE_PREFIX}${name}=${encodeURIComponent(value)}; path=/`;
};

const defaultRoutes = (): Route[] => [
  { method: 'GET', match: /\/repairers/, json: REPAIRERS },
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/entities\?/, json: DOCUMENTS },
  { method: 'GET', match: /\/baselines/, json: BASELINES },
  { method: 'POST', match: /\/scan$/, json: SCAN_RESULT },
];

async function mountRepair(routes = defaultRoutes(), query = '?feature=repair&projectId=elibrary') {
  installFetchMock(routes);
  setUrl(query);
  render(<App />);
  await vi.waitFor(() => expect(document.body.textContent).toContain('Enumeration fields: Invalid value'), {
    timeout: 5000,
  });
}

const numericInputs = () => Array.from(document.querySelectorAll<HTMLInputElement>('input[inputmode="numeric"]'));
const repairerCheckboxes = () =>
  Array.from(document.querySelectorAll<HTMLInputElement>('.repairer-card input[type="checkbox"]'));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
  COOKIES.forEach((c) => {
    document.cookie = `${COOKIE_PREFIX}${c}=; path=/; max-age=0`;
  });
});

describe('Repair form restored from cookies', () => {
  it('restores every saved scan parameter instead of the defaults', async () => {
    setCookie('entityType', 'DOCUMENT');
    // Documents default to picking entities from a dropdown, so the saved query is only on screen in the
    // saved QUERY mode - which is itself one of the restored parameters.
    setCookie('filterMode', 'QUERY');
    setCookie('userQuery', 'type:requirement');
    setCookie('revision', '4321');
    setCookie('sort', 'id');
    setCookie('limit', '25');
    setCookie('timeout', '120');
    setCookie('hideValid', 'true');
    await mountRepair();

    const values = numericInputs().map((i) => i.value);
    expect(values).toContain('25'); // limit
    expect(values).toContain('120'); // timeout
    expect(values).toContain('4321'); // revision
    const query = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="text"]')).find(
      (i) => i.value === 'type:requirement',
    );
    expect(query).toBeTruthy();
    // hideValid is a checkbox outside the repairer cards.
    const hideValid = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')).find(
      (c) => !c.closest('.repairer-card'),
    );
    expect(hideValid?.checked).toBe(true);
  });

  it('falls back to the defaults when the saved values are not positive numbers', async () => {
    setCookie('entityType', 'NOT_A_TYPE'); // not in the option list -> WORKITEM
    setCookie('revision', 'abc');
    setCookie('limit', '0');
    setCookie('timeout', '-5');
    await mountRepair();

    const values = numericInputs().map((i) => i.value);
    expect(values).toContain('100'); // default limit
    expect(values).toContain('60'); // default timeout
    // A revision of 0 renders as an empty field rather than a literal zero.
    expect(values).not.toContain('0');
  });

  it('drops a saved subtype that the loaded type list no longer offers', async () => {
    setCookie('entitySubtype', 'no-such-subtype');
    await mountRepair();
    const selects = Array.from(document.querySelectorAll<HTMLSelectElement>('select'));
    expect(selects.every((s) => s.value !== 'no-such-subtype')).toBe(true);
  });
});

describe('Repair repairer selection restored from cookies', () => {
  it('ignores a saved selection that no longer matches any available repairer', async () => {
    setCookie('repairers_WORKITEM', 'GoneRepairer,AlsoGone');
    await mountRepair();
    await vi.waitFor(() => expect(repairerCheckboxes().length).toBeGreaterThan(0));
    // Nothing of the saved list survives the filter, so the default selection applies instead of
    // leaving every repairer unticked (which would make Scan useless).
    expect(repairerCheckboxes().some((c) => c.checked)).toBe(true);
  });

  it('restores a saved repairer setting over its declared default', async () => {
    const repairer = REPAIRERS[0];
    const config = repairer.configs[0];
    // Every real repairer config ships off (see the fixture comment), so a saved "true" is the case that
    // proves the cookie wins over the declared default - saving "false" would match the default anyway.
    expect(config.defaultValue).toBe(false);
    setCookie(`rc_${repairer.id}_${config.key}`, 'true');
    await mountRepair();
    const card = Array.from(document.querySelectorAll<HTMLElement>('.repairer-card')).find((c) =>
      c.textContent?.includes(repairer.name),
    )!;
    const setting = card.querySelector<HTMLInputElement>('.repairer-setting input[type="checkbox"]');
    await vi.waitFor(() => expect(setting).not.toBeNull());
    expect(setting!.checked).toBe(true);
  });
});
