import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page, userEvent } from 'vitest/browser';
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
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only snapshots of the Scan & Repair page: the initial parameter/repairers panel for the default
// Work Items entity type, the same panel with Documents selected (document query hint + the larger
// document repairer set behind the collapsed summary), the Advanced block expanded, the Repairers block
// expanded (repairer cards + per-repairer settings), and the results table after a scan (items, issue
// counts, repairer breakdown link).
//
// The results table is captured in four states, because its controls are keyboard operable and the look
// of each state is what the pixels have to hold: collapsed, fully expanded with the spent Expand all
// still focused, frozen by a batch repair, and with a warning popup opened by focus alone.

const origUrl = window.location.pathname + window.location.search;

// `holdRepair` leaves POST /repair unsettled, so the page stays in its batch-repair state for as long
// as a capture needs. Nothing resolves it; the test file's cleanup drops the page.
const routes = (holdRepair = false): Route[] => [
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
      if (holdRepair) {
        return new Promise<Response>(() => {});
      }
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

async function mount(holdRepair = false) {
  installFetchMock(routes(holdRepair));
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
  await settleLayout();
  await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

// Runs the scan every results shot starts from.
async function scan() {
  Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .find((b) => (b.textContent ?? '').trim() === 'Scan')!
    .click();
  await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
}

// The two expand-all controls carry the same class and differ only by their accessible name.
function expandAllControl(label: string): HTMLButtonElement {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('.expand-all-btn')).find(
    (b) => b.getAttribute('aria-label') === label,
  )!;
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
    // Every row collapsed: the arrow of a row with issues (a button) beside the arrow of one without
    // (a span, out of the tab order), the warning marker on EL-100, and Collapse all already spent.
    await mount();
    await scan();
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    await captureApp('repair-results');
  });

  it('results expanded from the keyboard (open rows, sub-item rows, Expand all keeping its focus)', async () => {
    // What the collapsed shot cannot hold: the flipped arrows, the issue lists behind them, and the
    // sub-item rows with arrows of their own. Expand all is activated with Enter here, because the
    // state worth locking is the one after that: spent, and still holding the focus ring. A real
    // `disabled` would have handed focus to <body> in the same render as the keypress.
    await mount();
    await scan();
    const expandAll = expandAllControl('Expand all');
    expandAll.focus();
    await userEvent.keyboard('{Enter}');
    await vi.waitFor(() => expect(document.querySelectorAll('tr.subitem-row').length).toBeGreaterThan(0));
    expect(expandAll.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(expandAll);
    // Asserted rather than assumed: without it the reference would lock a ring that is not there and
    // claim the keyboard affordance is visible.
    expect(expandAll.matches(':focus-visible')).toBe(true);
    await captureApp('repair-results-expanded');
  });

  it('results frozen by a batch repair (the disabled look the mouse-only guard never had)', async () => {
    // `pointer-events: none` dimmed nothing, so while the controls were spans this state had no look of
    // its own. They are buttons now and take the disabled look from aria-disabled, which is what this
    // shot holds: both expand-all controls greyed, every checkbox disabled, the table behind them.
    await mount(true);
    await scan();
    document.querySelector<HTMLInputElement>('.issues-table thead .col-checkbox input')!.click();
    const repair = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').startsWith('Repair'),
    )!;
    await vi.waitFor(() => expect(repair.disabled).toBe(false));
    repair.click();
    await vi.waitFor(() => expect(document.querySelector('.issues-table.disabled')).not.toBeNull());
    expect(expandAllControl('Expand all').getAttribute('aria-disabled')).toBe('true');
    expect(expandAllControl('Collapse all').getAttribute('aria-disabled')).toBe('true');
    await captureApp('repair-results-repairing');
  });

  it('results with the warning popup open on focus (not hover alone)', async () => {
    // The popup is display:none until :hover or :focus-within, and only the pointer used to reach it.
    // Focusing the marker is the whole fix, so the shot is taken with focus on it and the pointer
    // parked elsewhere: what it shows is what the keyboard now reveals.
    await mount();
    await scan();
    const marker = document.querySelector<HTMLElement>('.warning-icon')!;
    marker.focus();
    expect(document.activeElement).toBe(marker);
    const popup = document.querySelector<HTMLElement>('.warning-popup')!;
    await vi.waitFor(() => expect(getComputedStyle(popup).display).toBe('flex'));
    await captureApp('repair-results-warning');
  });
});
