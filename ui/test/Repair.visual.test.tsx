import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import appIcon from '../src/assets/app-icon.svg';
import type { EntityType } from '../src/types';
import {
  BASELINES,
  DOCUMENT_TYPES,
  REPAIRERS_BY_ENTITY_TYPE,
  SCAN_RESULT,
  WORK_ITEM_TYPES,
  entitiesFor,
  repairersFor,
} from './fixtures';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';
import { settleBeforeCapture } from './visualHelpers';

// Docker-only snapshots of the Scan & Repair page: the initial parameter/repairers panel for the default
// Work Items entity type, the same panel with Documents selected (document query hint + the larger
// document repairer set behind the collapsed summary), the Advanced block expanded, the Repairers block
// expanded (repairer cards + per-repairer settings), and the results table after a scan (items, issue
// counts, repairer breakdown link).

const origUrl = window.location.pathname + window.location.search;

const routes = (): Route[] => [
  // Answers per entityType exactly like the backend, so switching the dropdown reloads a different list.
  { method: 'GET', match: /\/repairers/, respond: (url) => jsonResponse(repairersFor(url)) },
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/entities\?/, respond: (url) => jsonResponse(entitiesFor(url)) },
  { method: 'GET', match: /\/baselines/, json: BASELINES },
  { method: 'POST', match: /\/scan$/, json: SCAN_RESULT },
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

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  document.cookie.split('; ').forEach((c) => {
    const name = c.split('=')[0];
    if (name.startsWith('xmlRepair_')) document.cookie = `${name}=; path=/; max-age=0`;
  });
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

// The entity-type icons point at Polarion-served SVGs (/polarion/ria/…) that don't exist under test,
// so they'd render as broken images. Repoint every dropdown icon (the in-place trigger icon and, when
// the popup is open, its option icons) at this app's bundled app-icon.svg so the screenshots show a
// real icon. Vite resolves the import to a served URL, and we wait for each swapped image to load.
async function stubEntityIcons() {
  await vi.waitFor(() => {
    const img = document.querySelector<HTMLImageElement>('img.sd-trigger-icon');
    expect(img?.getAttribute('src')).toBeTruthy();
  });
  const imgs = Array.from(
    document.querySelectorAll<HTMLImageElement>('img.sd-trigger-icon, img.option-icon, img.sd-chip-icon'),
  );
  await Promise.all(
    imgs.map(
      (img) =>
        new Promise<void>((resolve) => {
          img.addEventListener('load', () => resolve(), { once: true });
          img.addEventListener('error', () => resolve(), { once: true });
          img.src = appIcon;
        }),
    ),
  );
}

async function mount() {
  installFetchMock(routes());
  // embedded=true mirrors how the navigation extender opens the page in Polarion: the PageLayout title
  // shows but the dev-only "Overview" back link is hidden, so the snapshot captures the production look.
  window.history.replaceState({}, '', '?feature=repair&projectId=elibrary&embedded=true');
  render(<App />);
  await vi.waitFor(() => expect(document.body.textContent).toContain('Enumeration fields: Invalid value'));
  await stubEntityIcons();
}

// Switches the entity type through the native <select> the SearchableDropdown is built on (what a click
// on a dropdown option ends up doing) and waits for the reloaded repairer list of that type. The trigger
// icon is re-rendered by the dropdown, so it has to be stubbed again.
async function selectEntityType(entityType: EntityType) {
  const select = document.querySelector<HTMLSelectElement>('.form-row select')!;
  select.value = entityType;
  select.dispatchEvent(new Event('change', { bubbles: true }));
  const total = REPAIRERS_BY_ENTITY_TYPE[entityType].length;
  await vi.waitFor(() =>
    expect(document.querySelector('.repairers-count')?.textContent).toContain(`/${total} selected`),
  );
  await stubEntityIcons();
}

// Picks entities in the multi-select the way a click on the dropdown's checkbox options does, then waits
// for the chips the trigger renders for them.
async function pickEntities(...keys: string[]) {
  const select = document.querySelector<HTMLSelectElement>('.filter-control select[multiple]')!;
  await vi.waitFor(() => expect(select.options.length).toBeGreaterThan(0));
  for (const option of Array.from(select.options)) {
    option.selected = keys.includes(option.value);
  }
  select.dispatchEvent(new Event('change', { bubbles: true }));
  await vi.waitFor(() => expect(document.querySelectorAll('.sd-chip').length).toBe(keys.length));
  await stubEntityIcons();
}

async function captureApp(name: string) {
  const app = document.querySelector('.app') as HTMLElement;
  await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

describe.skipIf(!__PIXEL_REFERENCES__)('Scan & Repair page visual', () => {
  it('initial (parameters + repairers panel)', async () => {
    await mount();
    await captureApp('repair-initial');
  });

  it('documents selected (document picker with chips + document repairers)', async () => {
    // Same view as repair-initial but for the Documents entity type: the filter row becomes the document
    // multi-select (two documents picked, each rendered as a removable chip, next to the mode toggle),
    // and the collapsed Repairers summary counts the document repairer set (13 of them, one deselected by
    // default) instead of the six work item ones.
    await mount();
    await selectEntityType('DOCUMENT');
    await pickEntities('_default/specification', 'Requirements/srs');
    await captureApp('repair-documents');
  });

  it('documents in query mode (the Lucene query field behind the mode toggle)', async () => {
    // The escape hatch from the picker: the same row carries the query input and the toggle flips back to
    // the selection. This is the only view where the query placeholder of a non-default entity type shows.
    await mount();
    await selectEntityType('DOCUMENT');
    document.querySelector<HTMLButtonElement>('.filter-mode-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('#user-query')).not.toBeNull());
    await captureApp('repair-documents-query');
  });

  it('collections selected (collection picker with chips)', async () => {
    // The other entity type with a picker. A collection is addressed by id alone, so its chips carry no
    // space suffix, and it has no subtype list - the icon on each option is the entity type's own.
    await mount();
    await selectEntityType('COLLECTION');
    await pickEntities('42', '43');
    await captureApp('repair-collections');
  });

  it('collections in query mode (the Lucene query field behind the mode toggle)', async () => {
    // Same escape hatch as for documents, with the collection query placeholder.
    await mount();
    await selectEntityType('COLLECTION');
    document.querySelector<HTMLButtonElement>('.filter-mode-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('#user-query')).not.toBeNull());
    await captureApp('repair-collections-query');
  });

  it('advanced expanded (all scan parameters)', async () => {
    await mount();
    const details = document.querySelector<HTMLDetailsElement>('.advanced-section')!;
    details.open = true;
    await captureApp('repair-advanced');
  });

  it('repairers expanded (cards + per-repairer settings)', async () => {
    // Documents carry the largest repairer set, so this shot covers every card variant: the opt-out
    // ModuleStandardStructureLinkRoleRepairer as an unchecked card with only its name/description
    // (settings render only under a checked repairer), checked cards without settings, and checked cards
    // with one or two settings. Every real config defaults to off, so the first one is ticked here to
    // capture both the checked and the unchecked setting box.
    await mount();
    await selectEntityType('DOCUMENT');
    const details = document.querySelector<HTMLDetailsElement>('.repairers-section')!;
    details.open = true;
    const firstSetting = document.querySelector<HTMLInputElement>('.repairer-setting input[type="checkbox"]')!;
    firstSetting.click();
    await vi.waitFor(() => expect(firstSetting.checked).toBe(true));
    await captureApp('repair-repairers');
  });

  it('results (issues table + breakdown)', async () => {
    await mount();
    Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => (b.textContent ?? '').trim() === 'Scan')!
      .click();
    await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    await captureApp('repair-results');
  });
});
