import { pageViolations } from '@sbb-polarion/react-sbb-polarion/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import type { ScanParams } from '../src/types';
import { DOCUMENTS, DOCUMENT_TYPES, LINK_ROLES, STRUCTURE_LINK_SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type FetchMock, type Route, installFetchMock } from './mockFetch';

// The Structural link page. What is worth asserting here is what the other pages do not do: it offers
// documents alone, it sends the picked role as the repairer's `targetRole` config, and the repair request
// carries that same config.

const origUrl = window.location.pathname + window.location.search;

const routes = (): Route[] => [
  { method: 'GET', match: /\/work-item-types/, json: WORK_ITEM_TYPES },
  { method: 'GET', match: /\/document-types/, json: DOCUMENT_TYPES },
  { method: 'GET', match: /\/link-roles/, json: LINK_ROLES },
  { method: 'GET', match: /\/entities\?/, json: DOCUMENTS },
  { method: 'POST', match: /\/scan$/, json: STRUCTURE_LINK_SCAN_RESULT },
  {
    method: 'POST',
    match: /\/repair$/,
    json: [
      { issueMetaInfo: 'sl-1', success: true, warnings: [] },
      { issueMetaInfo: 'sl-2', success: true, warnings: [] },
    ],
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

// Matched by prefix: the repair button appends the selected count once something is ticked.
const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
    (b.textContent ?? '').trim().startsWith(label),
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};

const bodyOf = (fetchMock: FetchMock, method: string, fragment: string): ScanParams => {
  const call = fetchMock.mock.calls.find(
    ([url, init]) => String(url).includes(fragment) && (init?.method ?? 'GET') === method,
  );
  if (!call) throw new Error(`no ${method} request to ${fragment}`);
  return JSON.parse(String(call[1]?.body));
};

async function mount(): Promise<FetchMock> {
  const fetchMock = installFetchMock(routes());
  window.history.replaceState({}, '', '?feature=structural-link&projectId=elibrary&embedded=true');
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.form-section')).not.toBeNull());
  return fetchMock;
}

async function runScan() {
  button('Scan').click();
  await vi.waitFor(() => expect(document.querySelector('.issues-table')).not.toBeNull());
}

describe('Structural link page', () => {
  it('loads the project link roles', async () => {
    const fetchMock = await mount();
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/link-roles?projectId=elibrary'))).toBe(true),
    );
  });

  it('scans documents only, with the default role as the target', async () => {
    const fetchMock = await mount();
    await runScan();

    const body = bodyOf(fetchMock, 'POST', '/scan');
    expect(body.entityType).toBe('DOCUMENT');
    expect(body.repairers).toEqual(['ModuleStructureLinkRoleRepairer']);
    // 'relates_to' is the first role LINK_ROLES offers once the selected one is excluded.
    expect(body.configs).toEqual({
      ModuleStructureLinkRoleRepairer: {
        targetRole: 'parent',
        existingLinks: 'INTERRUPT',
        existingLinksRole: 'relates_to',
      },
    });
    // Writing is the point of the page, so it never resolves anything at a revision.
    expect(body.revision).toBeNull();
  });

  it('lists a row per document the scan reported, with the collision warning', async () => {
    await mount();
    await runScan();

    const text = document.querySelector('.issues-table')?.textContent ?? '';
    expect(text).toContain('DOC-1');
    expect(text).toContain('DOC-2');
  });

  it('shows the role each document uses instead of an issue count', async () => {
    await mount();
    await runScan();

    const roleCells = Array.from(document.querySelectorAll('.issues-table tbody .col-issues')).map((cell) =>
      (cell.textContent ?? '').trim(),
    );
    // The third document reports nothing, so it is already on the selected role and names it rather than a zero.
    expect(roleCells).toEqual(['relates_to', 'verifies', 'parent']);
  });

  it('drops the reported documents when the breakdown row is hidden, and restores them on show', async () => {
    await mount();
    await runScan();
    expect(document.querySelectorAll('.issues-table tbody tr').length).toBe(3);

    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());

    const filter = () => document.querySelector<HTMLButtonElement>('.breakdown-filter')!;
    expect(filter().textContent).toContain('hide');
    filter().click();
    // Only the document that reported nothing survives: it is not a finding to begin with.
    await vi.waitFor(() => expect(document.querySelectorAll('.issues-table tbody tr').length).toBe(1));

    expect(filter().textContent).toContain('show');
    filter().click();
    await vi.waitFor(() => expect(document.querySelectorAll('.issues-table tbody tr').length).toBe(3));
  });

  it('sends the same target role config when changing the role', async () => {
    const fetchMock = await mount();
    await runScan();

    document.querySelector<HTMLInputElement>('.issues-table input[type="checkbox"]')!.click();
    await vi.waitFor(() => expect(button('Change role').disabled).toBe(false));
    button('Change role').click();

    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url, init]) => String(url).includes('/repair') && init?.method === 'POST'),
      ).toBe(true),
    );
    const body = JSON.parse(String(bodyOfRaw(fetchMock)));
    expect(body.configs).toEqual({
      ModuleStructureLinkRoleRepairer: {
        targetRole: 'parent',
        existingLinks: 'INTERRUPT',
        existingLinksRole: 'relates_to',
      },
    });
    expect(body.issueMetaInfos.length).toBeGreaterThan(0);
  });

  it('names the selected role in the panel title, so it follows the top filter', async () => {
    await mount();

    const title = document.querySelector('.existing-links-title');
    expect(title?.textContent).toContain('Existing parent links');
    expect(title?.querySelector('.existing-links-role')?.textContent).toBe('parent');
  });

  it('starts on interrupt, with the replacement role disabled until it is chosen', async () => {
    await mount();

    expect(radio('INTERRUPT').checked).toBe(true);
    await vi.waitFor(() => expect(replacementSelect()?.getAttribute('aria-disabled')).toBe('true'));

    radio('CHANGE').click();
    await vi.waitFor(() => expect(replacementSelect()?.getAttribute('aria-disabled')).not.toBe('true'));
  });

  it('offers the four answers, the two that write nothing first', async () => {
    await mount();

    const labels = Array.from(document.querySelectorAll('.existing-links-row label')).map((l) =>
      (l.textContent ?? '').trim(),
    );
    expect(labels).toEqual(['Interrupt modification', 'Ignore', 'Change link to', 'Delete link']);
    // Only 'Ignore' explains itself, because it is the one whose effect happens later and elsewhere.
    expect(document.querySelectorAll('.existing-links-row .help-icon').length).toBe(1);
  });

  it('sends the chosen answer for the existing links', async () => {
    const fetchMock = await mount();
    radio('DELETE').click();
    await runScan();

    expect(bodyOf(fetchMock, 'POST', '/scan').configs).toEqual({
      ModuleStructureLinkRoleRepairer: {
        targetRole: 'parent',
        existingLinks: 'DELETE',
        existingLinksRole: 'relates_to',
      },
    });
  });
});

const radio = (id: string): HTMLInputElement => {
  const found = document.querySelector<HTMLInputElement>(`#existing-links-${id}`);
  if (!found) throw new Error(`radio "${id}" not found`);
  return found;
};

/** The trigger of the "Change link to" select, the only one inside the Existing links panel. */
const replacementSelect = (): HTMLElement | null =>
  document.querySelector<HTMLElement>('.existing-links-section .sd-trigger');

/** The body of the repair request, which is not a ScanParams and so does not go through `bodyOf`. */
function bodyOfRaw(fetchMock: FetchMock): string {
  const call = fetchMock.mock.calls.find(([url, init]) => String(url).includes('/repair') && init?.method === 'POST');
  if (!call) throw new Error('no POST request to /repair');
  return String(call[1]?.body);
}

describe('Structural link page, accessibility', () => {
  it('names both link role controls', async () => {
    await mount();
    expect(page.getByRole('combobox', { name: /^Structure link role/ }).element()).toBeVisible();
    expect(page.getByRole('combobox', { name: 'Replacement link role' }).element()).toBeVisible();
  });

  it('has no WCAG A/AA violations on the form', async () => {
    await mount();
    radio('CHANGE').click();
    await vi.waitFor(() => expect(replacementSelect()?.getAttribute('aria-disabled')).not.toBe('true'));
    expect(await pageViolations()).toEqual([]);
  });

  it('has no WCAG A/AA violations with the scan results', async () => {
    await mount();
    await runScan();
    expect(await pageViolations()).toEqual([]);
  });
});
