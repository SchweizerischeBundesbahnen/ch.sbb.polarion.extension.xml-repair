import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { DOCUMENTS, DOCUMENT_TYPES, PURGE_SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Full behavior test of the Purge outdated data page, driven through the real App (feature router). The
// extension REST layer is mocked at the global fetch boundary, so no Polarion is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const defaultRoutes = (): Route[] => [
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/entities\?/, json: DOCUMENTS },
  { method: 'POST', match: /\/scan$/, json: PURGE_SCAN_RESULT },
  {
    method: 'POST',
    match: /\/repair$/,
    respond: (_url, init) => {
      const body = JSON.parse(String(init?.body));
      return jsonResponse(
        (body.issueMetaInfos as string[]).map((m) => ({ issueMetaInfo: m, success: true, warnings: [] })),
      );
    },
  },
];

const textButton = (label: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(
    (x) => (x.textContent ?? '').trim() === label,
  );
  if (!b) throw new Error(`button "${label}" not found`);
  return b;
};

const startsWithButton = (prefix: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((x) =>
    (x.textContent ?? '').trim().startsWith(prefix),
  );
  if (!b) throw new Error(`button starting with "${prefix}" not found`);
  return b;
};

/** The attribute cards of the left-hand panel, as "<id> filled in N item(s)" text. */
const attributeNames = (): string[] =>
  Array.from(document.querySelectorAll('.attributes-section .repairer-name')).map((n) => n.textContent ?? '');

/** The entity ids the results table currently lists, top-level rows and subitems alike. */
const resultEntities = (): string[] =>
  Array.from(document.querySelectorAll('.issues-table .entity-cell')).map((c) => (c.textContent ?? '').trim());

const attributeCheckbox = (attribute: string): HTMLInputElement => {
  const card = Array.from(document.querySelectorAll('.attributes-section .repairer-card')).find(
    (c) => c.querySelector('.repairer-name')?.textContent === attribute,
  );
  if (!card) throw new Error(`attribute card "${attribute}" not found`);
  return card.querySelector('input[type="checkbox"]') as HTMLInputElement;
};

let fetchMock: FetchMock;

async function mountPurge(routes = defaultRoutes(), query = '?feature=purge-outdated-data&projectId=elibrary') {
  fetchMock = installFetchMock(routes);
  setUrl(query);
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.attributes-section')).not.toBeNull());
}

async function runScan() {
  textButton('Scan').click();
  await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull());
}

const scanBody = () => {
  const call = fetchMock.mock.calls.find(([, init]) => String((init as RequestInit)?.method) === 'POST');
  return JSON.parse(String((call?.[1] as RequestInit)?.body));
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setUrl(origUrl);
  document.cookie.split('; ').forEach((c) => {
    const name = c.split('=')[0];
    if (name.startsWith('xmlRepair_')) document.cookie = `${name}=; path=/; max-age=0`;
  });
  window.top?.document.querySelectorAll('script[id^="sbb-breadcrumb-bridge"]').forEach((s) => s.remove());
});

describe('Purge outdated data page', () => {
  it('shows a placeholder instead of an attribute list before the first scan', async () => {
    await mountPurge();

    expect(document.body.textContent).toContain('Run a scan to see which attributes are filled but not defined');
    expect(attributeNames()).toEqual([]);
    expect(document.querySelector('.results-section')).toBeNull();
  });

  it('offers no revision filter, and never asks for the project baselines', async () => {
    // Purging writes, and the backend refuses to write anything resolved at a revision, so the row would only
    // ever lead to a scan whose findings cannot be acted on.
    await mountPurge();
    document.querySelector<HTMLDetailsElement>('.advanced-section')!.open = true;

    expect(document.body.textContent).not.toContain('Revision/Baseline');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/baselines'))).toBe(false);
  });

  it('pins the scan to HEAD even when a revision was remembered under its own cookies', async () => {
    document.cookie = 'xmlRepair_purge_revision=4321; path=/';
    await mountPurge();
    await runScan();

    expect(scanBody().revision).toBeNull();
  });

  it('scans with the purge repairer only, hiding valid items by default', async () => {
    await mountPurge();
    await runScan();

    const body = scanBody();
    expect(body.repairers).toEqual(['OutdatedCustomFieldsRepairer']);
    // An entity with nothing outdated is noise on this page, so it starts filtered out.
    expect(body.hideValid).toBe(true);
    expect(body.projectId).toBe('elibrary');
  });

  it('lists the attributes the scan found, sorted, with how many items hold each', async () => {
    await mountPurge();
    await runScan();

    expect(attributeNames()).toEqual(['legacyOwner', 'obsoleteFlag', 'oldEstimate']);
    const legacyOwnerCard = attributeCheckbox('legacyOwner').closest('.repairer-card');
    expect(legacyOwnerCard?.textContent).toContain('filled in 2 items');
    expect(attributeCheckbox('obsoleteFlag').closest('.repairer-card')?.textContent).toContain('filled in 1 item');
  });

  it('starts with every found attribute ticked, so all matching items show', async () => {
    await mountPurge();
    await runScan();

    expect(attributeCheckbox('legacyOwner').checked).toBe(true);
    expect(attributeCheckbox('oldEstimate').checked).toBe(true);
    // Four filled attributes across the three scanned items (legacyOwner twice).
    expect(document.body.textContent).toContain('4 outdated attributes in 3 items');
    expect(resultEntities().join(' ')).toContain('EL-100');
    expect(resultEntities().join(' ')).toContain('EL-200');
  });

  it('unticking an attribute drops the items that only had that one', async () => {
    await mountPurge();
    await runScan();
    expect(resultEntities().join(' ')).toContain('EL-200');

    attributeCheckbox('legacyOwner').click();

    // EL-200 only had legacyOwner, so it goes; EL-100 stays through oldEstimate.
    await vi.waitFor(() => expect(resultEntities().join(' ')).not.toContain('EL-200'));
    expect(resultEntities().join(' ')).toContain('EL-100');
    expect(attributeCheckbox('legacyOwner').checked).toBe(false);
  });

  it('unticking every attribute leaves nothing to purge', async () => {
    await mountPurge();
    await runScan();

    const selectAll = document.querySelector('.attributes-section .select-all input') as HTMLInputElement;
    selectAll.click();

    await vi.waitFor(() => expect(attributeCheckbox('legacyOwner').checked).toBe(false));
    expect(resultEntities()).toEqual([]);
    expect(textButton('Purge').disabled).toBe(true);
  });

  it('purges the selected items through the repair endpoint and marks them done', async () => {
    await mountPurge();
    await runScan();

    const rowCheckbox = document.querySelector('.issues-table tbody input[type="checkbox"]') as HTMLInputElement;
    rowCheckbox.click();
    await vi.waitFor(() => expect(startsWithButton('Purge (attributes:').disabled).toBe(false));

    startsWithButton('Purge (attributes:').click();

    await vi.waitFor(() => expect(document.querySelector('.fixed-badge')).not.toBeNull());
    const purgeCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/repair'));
    expect(purgeCall).toBeDefined();
    const body = JSON.parse(String((purgeCall?.[1] as RequestInit)?.body));
    // EL-100 is the first row and holds both of its attributes' issues.
    expect(body.issueMetaInfos).toEqual(['purge-1', 'purge-2']);
    expect(body.configs).toEqual({});
  });

  it('keeps the Purge button disabled until something is selected', async () => {
    await mountPurge();
    await runScan();

    const purge = textButton('Purge');
    expect(purge.disabled).toBe(true);
    expect(purge.title).toContain('at least one item');
  });

  it('surfaces a failed scan and shows no results', async () => {
    await mountPurge([
      ...defaultRoutes().filter((r) => !/scan/.test(r.match.source)),
      { method: 'POST', match: /\/scan$/, json: { message: 'Lucene query is broken' }, status: 400 },
    ]);

    textButton('Scan').click();

    await vi.waitFor(() => expect(document.querySelector('.error-message')).not.toBeNull());
    expect(document.querySelector('.error-message')?.textContent).toContain('Lucene query is broken');
    expect(document.querySelector('.results-section')).toBeNull();
  });

  it('refuses to scan without a project in the URL', async () => {
    await mountPurge(defaultRoutes(), '?feature=purge-outdated-data');

    textButton('Scan').click();

    await vi.waitFor(() => expect(document.querySelector('.error-message')).not.toBeNull());
    expect(document.querySelector('.error-message')?.textContent).toContain('Project ID is missing');
  });

  it('discards a result when the entity type changes', async () => {
    await mountPurge();
    await runScan();
    expect(document.querySelector('.results-section')).not.toBeNull();

    // SearchableSelect is backed by a native select; the Entity Type row is the first one.
    const select = document.querySelector<HTMLSelectElement>('.form-row select')!;
    select.value = 'DOCUMENT';
    select.dispatchEvent(new Event('change', { bubbles: true }));

    await vi.waitFor(() => expect(document.querySelector('.results-section')).toBeNull());
    expect(attributeNames()).toEqual([]);
  });
});
