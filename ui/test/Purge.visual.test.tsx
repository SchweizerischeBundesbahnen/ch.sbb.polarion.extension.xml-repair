import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import appIcon from '../src/assets/app-icon.svg';
import { BASELINES, DOCUMENTS, DOCUMENT_TYPES, PURGE_SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type Route, installFetchMock } from './mockFetch';

// Docker-only snapshots of the Purge outdated data page: the initial state, where the attribute block can
// only show its placeholder because nothing has been scanned yet; the advanced parameters, which carry no
// revision row here; the results with every found attribute ticked and the attribute list expanded; and the
// attribute breakdown of the results table.

const origUrl = window.location.pathname + window.location.search;

const routes = (): Route[] => [
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/entities\?/, json: DOCUMENTS },
  { method: 'GET', match: /\/baselines/, json: BASELINES },
  { method: 'POST', match: /\/scan$/, json: PURGE_SCAN_RESULT },
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  document.cookie.split('; ').forEach((c) => {
    const name = c.split('=')[0];
    if (name.startsWith('xmlRepair_')) document.cookie = `${name}=; path=/; max-age=0`;
  });
  window.top?.document.querySelectorAll('script[id^="sbb-breadcrumb-bridge"]').forEach((s) => s.remove());
});

// The entity-type icons point at Polarion-served SVGs that do not exist under test, so they would render
// broken. Repoint them at this app's bundled icon, as the Scan & Repair visual test does.
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
  // embedded=true mirrors how the navigation node opens the page in Polarion.
  window.history.replaceState({}, '', '?feature=purge-outdated-data&projectId=elibrary&embedded=true');
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.attributes-section')).not.toBeNull());
  await stubEntityIcons();
}

async function runScan() {
  Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
    .find((b) => (b.textContent ?? '').trim() === 'Scan')!
    .click();
  await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
}

async function captureApp(name: string) {
  const app = document.querySelector('.app') as HTMLElement;
  await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

describe.skipIf(!__PIXEL_REFERENCES__)('Purge outdated data page visual', () => {
  it('initial (parameters + the empty attribute block)', async () => {
    await mount();
    document.querySelector<HTMLDetailsElement>('.attributes-section')!.open = true;
    await captureApp('purge-initial');
  });

  it('advanced expanded (the scan parameters, without a revision row)', async () => {
    await mount();
    document.querySelector<HTMLDetailsElement>('.advanced-section')!.open = true;
    await captureApp('purge-advanced');
  });

  it('results (attribute cards + the items holding them)', async () => {
    await mount();
    await runScan();
    await vi.waitFor(() => expect(document.querySelectorAll('.attributes-section .repairer-card').length).toBe(3));
    await captureApp('purge-results');
  });

  it('attribute breakdown of the results table', async () => {
    await mount();
    await runScan();
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    await captureApp('purge-breakdown');
  });
});
