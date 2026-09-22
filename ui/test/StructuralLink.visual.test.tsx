import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import appIcon from '../src/assets/app-icon.svg';
import {
  DOCUMENTS,
  DOCUMENT_TYPES,
  LINK_ROLES,
  STRUCTURE_LINK_SCAN_RESULT,
  WORK_ITEM_TYPES,
  structureLinkCollisionRepair,
  structureLinkCollisionScan,
  structureLinkPartialRepair,
} from './fixtures';
import { type Route, installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only snapshots of the Structural link page: the initial state with the role row above the scan
// parameters and the "Existing links" answers below the buttons, that panel switched to "Change link to",
// the advanced parameters (no revision row, because the page only writes on HEAD), and the results, where the
// second document carries the warning about the links a switch would delete.

const origUrl = window.location.pathname + window.location.search;

const routes = (): Route[] => [
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/link-roles/, json: LINK_ROLES },
  { method: 'GET', match: /\/entities\?/, json: DOCUMENTS },
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

// The entity-type icons point at Polarion-served SVGs that do not exist under test, so they would render
// broken. Repoint them at this app's bundled icon, as the other page visual tests do.
async function stubEntityIcons() {
  // Only the icon-bearing triggers matter. The select carries a hidden, source-less icon element whenever its
  // option has no icon, which is the case for every link role - so the first one on this page is not the
  // entity type's.
  const iconTriggers = () =>
    Array.from(document.querySelectorAll<HTMLImageElement>('img.sd-trigger-icon')).filter((img) =>
      img.getAttribute('src'),
    );
  await vi.waitFor(() => expect(iconTriggers().length).toBeGreaterThan(0));
  const imgs = [
    ...iconTriggers(),
    ...Array.from(document.querySelectorAll<HTMLImageElement>('img.option-icon, img.sd-chip-icon')),
  ];
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

/** Only the reported documents, so hiding the single breakdown row leaves the table body empty. */
const FINDINGS_ONLY = {
  ...STRUCTURE_LINK_SCAN_RESULT,
  items: STRUCTURE_LINK_SCAN_RESULT.items.filter((item) => item.issues.length > 0),
};

async function mount(scanResult = STRUCTURE_LINK_SCAN_RESULT, extraRoutes: Route[] = []) {
  installFetchMock([...routes(), { method: 'POST', match: /\/scan$/, json: scanResult }, ...extraRoutes]);
  // embedded=true mirrors how the navigation node opens the page in Polarion.
  window.history.replaceState({}, '', '?feature=structural-link&projectId=elibrary&embedded=true');
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.advanced-section')).not.toBeNull());
  await stubEntityIcons();
}

const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
    (b.textContent ?? '').trim().startsWith(label),
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};

async function runScan() {
  Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .find((b) => (b.textContent ?? '').trim() === 'Scan')!
    .click();
  await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
}

async function captureApp(name: string) {
  const app = document.querySelector('.app') as HTMLElement;
  await settleLayout();
  await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

describe.skipIf(!__PIXEL_REFERENCES__)('Structural link page visual', () => {
  it('initial (role row + scan parameters)', async () => {
    await mount();
    await captureApp('structural-link-initial');
  });

  it('existing links set to change, which enables the replacement role', async () => {
    await mount();
    document.querySelector<HTMLInputElement>('#existing-links-CHANGE')!.click();
    await vi.waitFor(() =>
      expect(document.querySelector('.existing-links-section .sd-trigger')?.getAttribute('aria-disabled')).not.toBe(
        'true',
      ),
    );
    await captureApp('structural-link-existing-links');
  });

  it('advanced expanded (the scan parameters, without a revision row)', async () => {
    await mount();
    document.querySelector<HTMLDetailsElement>('.advanced-section')!.open = true;
    await captureApp('structural-link-advanced');
  });

  it('results (documents on another role, one warning about colliding links)', async () => {
    await mount();
    await runScan();
    await captureApp('structural-link-results');
  });

  it('breakdown of the results table, whose filter empties the list', async () => {
    await mount();
    await runScan();
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    await captureApp('structural-link-breakdown');
  });

  /**
   * A change the backend refused, so the row keeps its issue and shows why. Captured expanded, which is where
   * the warning lives. The link list is what grows with the number of collisions.
   */
  async function captureInterruptedChange(links: string[], name: string) {
    await mount(structureLinkCollisionScan(links), [
      { method: 'POST', match: /\/repair$/, json: structureLinkCollisionRepair(links) },
    ]);
    await runScan();

    document.querySelector<HTMLButtonElement>('.issues-table tbody .expand-arrow.clickable')!.click();
    document.querySelector<HTMLInputElement>('.issues-table tbody input[type="checkbox"]')!.click();
    await vi.waitFor(() => expect(button('Change role').disabled).toBe(false));
    button('Change role').click();

    await vi.waitFor(() => expect(document.querySelector('.issue-warnings')).not.toBeNull());

    // The failure also raises a toast, which floats over the results and fades on its own timer. Dropping it
    // keeps the capture about the row, and keeps the pixels the same between runs.
    document.querySelectorAll('[data-sonner-toaster]').forEach((el) => el.remove());
    await captureApp(name);
  }

  it('interrupted change, one colliding link', async () => {
    await captureInterruptedChange(['EL-232 -> EL-233'], 'structural-link-interrupted-one');
  });

  it('interrupted change, five colliding links', async () => {
    await captureInterruptedChange(
      ['EL-232 -> EL-233', 'EL-240 -> EL-241', 'EL-250 -> EL-251', 'EL-260 -> EL-261', 'EL-270 -> EL-271'],
      'structural-link-interrupted-many',
    );
  });

  it('completed change that could not touch every link', async () => {
    const links = ['EL-232 -> EL-233', 'EL-240 -> EL-241', 'EL-250 -> EL-251'];
    await mount(structureLinkCollisionScan(links), [
      {
        method: 'POST',
        match: /\/repair$/,
        json: structureLinkPartialRepair([links[0]], [links[1], links[2]]),
      },
    ]);
    await runScan();

    document.querySelector<HTMLButtonElement>('.issues-table tbody .expand-arrow.clickable')!.click();
    document.querySelector<HTMLInputElement>('.issues-table tbody input[type="checkbox"]')!.click();
    await vi.waitFor(() => expect(button('Change role').disabled).toBe(false));
    button('Change role').click();

    await vi.waitFor(() => expect(document.querySelectorAll('.issue-warnings li').length).toBe(4));
    document.querySelectorAll('[data-sonner-toaster]').forEach((el) => el.remove());
    await captureApp('structural-link-partial');
  });

  it('empty results table, whose Link Role header must stay on one line', async () => {
    await mount(FINDINGS_ONLY);
    await runScan();
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.breakdown-filter')!.click();
    await vi.waitFor(() => expect(document.querySelectorAll('.issues-table tbody tr').length).toBe(0));
    await captureApp('structural-link-empty');
  });
});
