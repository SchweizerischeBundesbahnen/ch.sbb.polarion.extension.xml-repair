import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import appIcon from '../src/assets/app-icon.svg';
import type { Repairer } from '../src/types';
import { BASELINES, DOCUMENT_TYPES, REPAIRERS, REPAIRERS_MANY, SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';

// Docker-only snapshots of the Scan & Repair page: the initial parameter/repairers panel, the same
// panel with the Advanced block expanded, the Repairers block expanded (repairer cards + per-repairer
// settings), and the results table after a scan (items, issue counts, repairer breakdown link).

const origUrl = window.location.pathname + window.location.search;

const routes = (repairers: Repairer[] = REPAIRERS): Route[] => [
  { method: 'GET', match: /\/repairers/, json: repairers },
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
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

async function mount(repairers: Repairer[] = REPAIRERS) {
  installFetchMock(routes(repairers));
  // embedded=true mirrors how the navigation extender opens the page in Polarion: the PageLayout title
  // shows but the dev-only "Overview" back link is hidden, so the snapshot captures the production look.
  window.history.replaceState({}, '', '?feature=repair&projectId=elibrary&embedded=true');
  render(<App />);
  await vi.waitFor(() => expect(document.body.textContent).toContain('Invalid enumeration value'));
  await stubEntityIcons();
}

describe('Scan & Repair page visual', () => {
  it('initial (parameters + repairers panel)', async () => {
    await mount();
    const app = document.querySelector('.app') as HTMLElement;
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await expect(page.elementLocator(app)).toMatchScreenshot('repair-initial');
  });

  it('advanced expanded (all scan parameters)', async () => {
    await mount();
    const details = document.querySelector<HTMLDetailsElement>('.advanced-section')!;
    details.open = true;
    const app = document.querySelector('.app') as HTMLElement;
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await expect(page.elementLocator(app)).toMatchScreenshot('repair-advanced');
  });

  it('repairers expanded (cards + per-repairer settings)', async () => {
    // Default selection checks every repairer except the opt-out ModuleStandardStructureLinkRoleRepairer,
    // so of the five we get four checked (each showing its settings) and one unchecked card with only its
    // name/description. Each setting's tick follows its config defaultValue, giving a mix of on/off boxes.
    await mount(REPAIRERS_MANY);
    const details = document.querySelector<HTMLDetailsElement>('.repairers-section')!;
    details.open = true;
    const app = document.querySelector('.app') as HTMLElement;
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await expect(page.elementLocator(app)).toMatchScreenshot('repair-repairers');
  });

  it('results (issues table + breakdown)', async () => {
    await mount();
    Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => (b.textContent ?? '').trim() === 'Scan')!
      .click();
    await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
    const app = document.querySelector('.app') as HTMLElement;
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await expect(page.elementLocator(app)).toMatchScreenshot('repair-results');
  });
});
