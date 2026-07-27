import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { BASELINES, DOCUMENT_TYPES, REPAIRERS, SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';

// The repair outcomes the main flow does not produce: every issue failing, every issue succeeding but
// with warnings, a rejected repair carrying a server message, deselecting the last selected issue, and
// a collection scan (which must not send a revision). Repair.test.tsx covers the happy paths.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const baseRoutes = (repair: Route): Route[] => [
  repair,
  { method: 'GET', match: /\/repairers/, json: REPAIRERS },
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/baselines/, json: BASELINES },
  { method: 'POST', match: /\/scan$/, json: SCAN_RESULT },
];

const textButton = (label: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(
    (x) => (x.textContent ?? '').trim() === label,
  );
  if (!b) throw new Error(`button "${label}" not found`);
  return b;
};

// The repair button carries a count, so it is matched by prefix rather than exact text.
const repairButton = (): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((x) =>
    (x.textContent ?? '').startsWith('Repair'),
  );
  if (!b) throw new Error('repair button not found');
  return b;
};

async function mountRepair(routes: Route[], query = '?feature=repair&projectId=elibrary') {
  installFetchMock(routes);
  setUrl(query);
  render(<App />);
  await vi.waitFor(() => expect(document.body.textContent).toContain('Invalid enumeration value'), { timeout: 5000 });
}

async function scanAndSelectAll() {
  textButton('Scan').click();
  await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });
  const headerCheckbox = document.querySelector<HTMLInputElement>('.issues-table thead .col-checkbox input')!;
  headerCheckbox.click();
  await vi.waitFor(() => expect(repairButton().disabled).toBe(false), { timeout: 5000 });
}

/** All issues of the scan result, answered with the given per-issue outcome. */
const repairRoute = (outcome: (meta: string) => Record<string, unknown>): Route => ({
  method: 'POST',
  match: /\/repair$/,
  respond: (_url, init) => {
    const body = JSON.parse(String(init?.body));
    return jsonResponse((body.issueMetaInfos as string[]).map(outcome));
  },
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
  document.cookie.split('; ').forEach((c) => {
    const name = c.split('=')[0];
    if (name.startsWith('xmlRepair_')) document.cookie = `${name}=; path=/; max-age=0`;
  });
});

describe('Repair outcomes', () => {
  it('reports a total failure when no issue could be repaired', async () => {
    await mountRepair(baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: false, warnings: [] }))));
    await scanAndSelectAll();
    repairButton().click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('Repair failed'), { timeout: 5000 });
  });

  it('reports success with warnings when every issue was repaired but flagged', async () => {
    await mountRepair(
      baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: ['check the result'] }))),
    );
    await scanAndSelectAll();
    repairButton().click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('repaired with warnings'), { timeout: 5000 });
  });

  it('surfaces the server message when the repair request is rejected', async () => {
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/repair$/,
        respond: () => jsonResponse({ message: 'repair service unavailable' }, 503) as Response & { status: number },
      }),
    );
    await scanAndSelectAll();
    repairButton().click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('repair service unavailable'), {
      timeout: 5000,
    });
  });
});

describe('Repair selection', () => {
  it('disables Repair again when the last selected issue is unticked', async () => {
    await mountRepair(baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: [] }))));
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });

    // Expand the first item and tick exactly one of its issues, then untick it again.
    const arrow = document.querySelector<HTMLElement>('tbody .expand-arrow.clickable')!;
    arrow.click();
    await vi.waitFor(() => expect(document.querySelector('.expand-row input[type="checkbox"]')).not.toBeNull());
    const issue = document.querySelector<HTMLInputElement>('.expand-row input[type="checkbox"]')!;
    issue.click();
    await vi.waitFor(() => expect(repairButton().disabled).toBe(false));

    issue.click();
    // Removing the last selected issue must drop the entity from the selection map, not leave an
    // empty entry that keeps the button enabled with nothing to repair.
    await vi.waitFor(() => expect(repairButton().disabled).toBe(true));
  });
});

describe('Repair inside a collection', () => {
  const collectionRow = () =>
    Array.from(document.querySelectorAll<HTMLTableRowElement>('.issues-table tbody tr')).find((r) =>
      (r.textContent ?? '').includes('COLL-1'),
    )!;

  it('repairs the issues of every selected sub-item and marks them fixed', async () => {
    let meta: string[] = [];
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/repair$/,
        respond: (_url, init) => {
          meta = JSON.parse(String(init?.body)).issueMetaInfos as string[];
          return jsonResponse(meta.map((m) => ({ issueMetaInfo: m, success: true, warnings: [] })));
        },
      }),
    );
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });

    // Expanding the collection reveals its sub-items; ticking the collection selects all of them.
    collectionRow().querySelector<HTMLElement>('.col-issues.clickable')!.click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('DOC-1'));
    collectionRow().querySelector<HTMLInputElement>('.col-checkbox input[type="checkbox"]')!.click();
    await vi.waitFor(() => expect(repairButton().disabled).toBe(false));

    repairButton().click();
    // The request carries the sub-items' issues, and the repaired rows get the fixed badge.
    await vi.waitFor(() => expect(meta.length).toBeGreaterThan(0), { timeout: 5000 });
    await vi.waitFor(() => expect(document.querySelector('.subitem-row .fixed-badge')).not.toBeNull(), {
      timeout: 5000,
    });
  });

  it('unticks the whole collection again on a second click', async () => {
    await mountRepair(baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: [] }))));
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });

    collectionRow().querySelector<HTMLElement>('.col-issues.clickable')!.click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('DOC-1'));
    const checkbox = collectionRow().querySelector<HTMLInputElement>('.col-checkbox input[type="checkbox"]')!;
    checkbox.click();
    await vi.waitFor(() => expect(repairButton().disabled).toBe(false));
    // Everything below is already selected, so the same click clears the whole subtree.
    collectionRow().querySelector<HTMLInputElement>('.col-checkbox input[type="checkbox"]')!.click();
    await vi.waitFor(() => expect(repairButton().disabled).toBe(true));
  });
});

describe('Repair scan parameters', () => {
  it('offers only usable baselines as revision hints', async () => {
    await mountRepair([
      {
        method: 'GET',
        match: /\/baselines/,
        json: [
          ...BASELINES,
          { revision: 'not-a-number', name: 'Broken' },
          { revision: '0', name: 'Zero' },
          { revision: '-5', name: 'Negative' },
        ],
      },
      ...baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: [] }))).filter(
        (r) => !/baselines/.test(String(r.match)),
      ),
    ]);
    // A baseline whose revision is not a positive number cannot be scanned at, so it is dropped from
    // the hint list rather than offered and failing later.
    expect(document.body.textContent).not.toContain('Broken');
    expect(document.body.textContent).not.toContain('Zero');
    expect(document.body.textContent).not.toContain('Negative');
  });

  it('sends the chosen subtype alongside the entity type', async () => {
    let body: Record<string, unknown> = {};
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/scan$/,
        respond: (_url, init) => {
          body = JSON.parse(String(init?.body));
          return jsonResponse(SCAN_RESULT);
        },
      }),
    );
    const entitySelect = document.querySelector<HTMLSelectElement>('select')!;
    const withSubtype = Array.from(entitySelect.options).find((o) => o.value.includes('::'));
    expect(withSubtype, 'an entity option carrying a subtype').toBeTruthy();
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(entitySelect, withSubtype!.value);
    entitySelect.dispatchEvent(new Event('change', { bubbles: true }));

    await vi.waitFor(() => expect(textButton('Scan').disabled).toBe(false), { timeout: 5000 });
    textButton('Scan').click();
    await vi.waitFor(() => expect(body.entitySubtype).toBeTruthy(), { timeout: 5000 });
    expect(withSubtype!.value).toContain(String(body.entitySubtype));
  });

  it('sends the revision and the free-text query when both are set', async () => {
    // Both fields are empty in a fresh browser, so the "present" side of each is only reached when a
    // previous session saved them.
    document.cookie = `xmlRepair_revision=4321; path=/`;
    document.cookie = `xmlRepair_userQuery=${encodeURIComponent('type:requirement')}; path=/`;
    let body: Record<string, unknown> = {};
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/scan$/,
        respond: (_url, init) => {
          body = JSON.parse(String(init?.body));
          return jsonResponse(SCAN_RESULT);
        },
      }),
    );
    textButton('Scan').click();
    await vi.waitFor(() => expect(body.userQuery).toBe('type:requirement'), { timeout: 5000 });
    // A work-item scan keeps the revision (only collection scans drop it).
    expect(body.revision).toBe('4321');
  });

  it('handles a result item that carries no subitems field at all', async () => {
    // Older backends omit `subitems` entirely instead of sending an empty array; the tree must treat
    // that as a leaf rather than dereferencing it.
    const item = SCAN_RESULT.items.find((i) => !i.subitems || i.subitems.length === 0) ?? SCAN_RESULT.items[0];
    const withoutSubitems = Object.fromEntries(Object.entries(item).filter(([k]) => k !== 'subitems'));
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/scan$/,
        json: { ...SCAN_RESULT, items: [withoutSubitems] },
      }),
    );
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });
    expect(document.querySelectorAll('.issues-table tbody tr').length).toBeGreaterThan(0);
  });

  it('hides a repairer from the results and brings it back on a second click', async () => {
    await mountRepair(baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: [] }))));
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull(), { timeout: 5000 });

    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-filter')).not.toBeNull());
    const filter = document.querySelector<HTMLButtonElement>('.breakdown-filter')!;
    filter.click();
    await vi.waitFor(() => expect(document.querySelector('.repairer-hidden')).not.toBeNull());

    // Clicking the same filter again un-hides it, and the issue counts come back with it.
    document.querySelector<HTMLButtonElement>('.breakdown-filter')!.click();
    await vi.waitFor(() => expect(document.querySelector('.repairer-hidden')).toBeNull());
  });

  it('clears the previous selection and expansion when scanning again', async () => {
    await mountRepair(baseRoutes(repairRoute((m) => ({ issueMetaInfo: m, success: true, warnings: [] }))));
    await scanAndSelectAll();
    document.querySelector<HTMLElement>('tbody .expand-arrow.clickable')!.click();
    await vi.waitFor(() => expect(document.querySelector('.expand-row')).not.toBeNull());

    textButton('Scan').click();
    // A fresh scan starts from a clean slate: nothing selected, nothing expanded.
    await vi.waitFor(() => expect(document.querySelector('.expand-row')).toBeNull(), { timeout: 5000 });
    expect(repairButton().disabled).toBe(true);
  });

  it('falls back to the status when a failed repair response has no message', async () => {
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/repair$/,
        respond: () => jsonResponse({}, 500),
      }),
    );
    await scanAndSelectAll();
    repairButton().click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('Repair failed with status 500'), {
      timeout: 5000,
    });
  });
});

describe('Repair collection scans', () => {
  it('never sends a revision when scanning a collection', async () => {
    let body: Record<string, unknown> = {};
    await mountRepair(
      baseRoutes({
        method: 'POST',
        match: /\/scan$/,
        respond: (_url, init) => {
          body = JSON.parse(String(init?.body));
          return jsonResponse(SCAN_RESULT);
        },
      }),
    );
    // Pick the collection entity type; a revision typed for work items must not leak into the request.
    const entitySelect = document.querySelector<HTMLSelectElement>('select')!;
    const collectionOption = Array.from(entitySelect.options).find((o) => o.value.startsWith('COLLECTION'));
    expect(collectionOption, 'a COLLECTION entity-type option').toBeTruthy();
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(
      entitySelect,
      collectionOption!.value,
    );
    entitySelect.dispatchEvent(new Event('change', { bubbles: true }));

    await vi.waitFor(() => expect(textButton('Scan').disabled).toBe(false), { timeout: 5000 });
    textButton('Scan').click();
    await vi.waitFor(() => expect(body.entityType).toBe('COLLECTION'), { timeout: 5000 });
    expect(body.revision).toBeNull();
  });
});
