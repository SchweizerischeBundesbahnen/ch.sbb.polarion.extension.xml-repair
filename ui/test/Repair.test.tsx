import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { BASELINES, DOCUMENT_TYPES, REPAIRERS, SCAN_RESULT, WORK_ITEM_TYPES } from './fixtures';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Full behavior test of the Scan & Repair page, driven through the real App (feature router). The
// extension REST layer is mocked at the global fetch boundary, so no Polarion is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const defaultRoutes = (): Route[] => [
  { method: 'GET', match: /\/repairers/, json: REPAIRERS },
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

const textButton = (label: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(
    (x) => (x.textContent ?? '').trim() === label,
  );
  if (!b) throw new Error(`button "${label}" not found`);
  return b;
};

let fetchMock: FetchMock;
async function mountRepair(routes = defaultRoutes(), query = '?feature=repair&projectId=elibrary') {
  fetchMock = installFetchMock(routes);
  setUrl(query);
  render(<App />);
  await vi.waitFor(() => expect(document.body.textContent).toContain('Invalid enumeration value'));
}

async function runScan() {
  textButton('Scan').click();
  await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull());
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setUrl(origUrl);
  // Clear the app's remember-selection cookies so tests don't leak state.
  document.cookie.split('; ').forEach((c) => {
    const name = c.split('=')[0];
    if (name.startsWith('xmlRepair_')) document.cookie = `${name}=; path=/; max-age=0`;
  });
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

const hasOverviewLink = (): boolean =>
  Array.from(document.querySelectorAll('a')).some((a) => (a.textContent ?? '').includes('Overview'));

describe('Scan & Repair page', () => {
  it('shows the dev Overview back link when not embedded', async () => {
    await mountRepair();
    expect(hasOverviewLink()).toBe(true);
  });

  it('hides the Overview back link when embedded (the Polarion navigation entry)', async () => {
    await mountRepair(defaultRoutes(), '?feature=repair&projectId=elibrary&embedded=true');
    expect(hasOverviewLink()).toBe(false);
  });

  it('loads the repairers with the opt-out one deselected by default', async () => {
    await mountRepair();
    // Both repairers listed; the opt-out repairer is off by default -> 1 of 2 selected.
    expect(document.body.textContent).toContain('Invalid enumeration value');
    expect(document.body.textContent).toContain('Module structure link role');
    expect(document.body.textContent).toContain('(1/2 selected)');
  });

  it('scans and shows the results with a repairer breakdown', async () => {
    await mountRepair();
    await runScan();
    // Three top-level items in the fixture.
    expect(document.body.textContent).toContain('Results');
    // The scan POST carried the project + selected repairers.
    const scanCall = fetchMock.mock.calls.find((c) => String(c[0]).endsWith('/scan'));
    const scanBody = JSON.parse(String(scanCall![1]!.body));
    expect(scanBody.projectId).toBe('elibrary');
    expect(scanBody.repairers).toContain('FieldsInvalidEnumerationValueRepairer');
    // Breakdown toggle opens the per-repairer table.
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-table')).not.toBeNull());
  });

  it('expands an item, selects all, and batch-repairs the selected issues', async () => {
    await mountRepair();
    await runScan();

    // Select all selectable issues via the header checkbox.
    const headerCheckbox = document.querySelector<HTMLInputElement>('.issues-table thead .col-checkbox input');
    expect(headerCheckbox).not.toBeNull();
    headerCheckbox!.click();

    // Repair button becomes enabled once there is a selection.
    await vi.waitFor(() => expect(textButton('Scan')).toBeTruthy());
    const repairBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').startsWith('Repair'),
    )!;
    await vi.waitFor(() => expect(repairBtn.disabled).toBe(false));
    repairBtn.click();

    // After repair the fixed badge (checkmark) appears on repaired rows.
    await vi.waitFor(() => expect(document.querySelector('.fixed-badge')).not.toBeNull());
    const repairCall = fetchMock.mock.calls.find((c) => String(c[0]).endsWith('/repair'));
    expect(repairCall).toBeTruthy();
    const repairBody = JSON.parse(String(repairCall![1]!.body));
    expect(repairBody.issueMetaInfos.length).toBeGreaterThan(0);
  });

  it('expands a single item to show its issue list and toggles one issue', async () => {
    await mountRepair();
    await runScan();
    // The first data row's issues cell is clickable -> expands the IssueList.
    const issuesCell = document.querySelector<HTMLElement>('.issues-table tbody .col-issues.clickable');
    issuesCell!.click();
    await vi.waitFor(() => expect(document.querySelector('.issue-list')).not.toBeNull());
    expect(document.body.textContent).toContain('Bad enum in EL-100');
    // Toggle the first issue checkbox in the expanded list.
    const issueCheckbox = document.querySelector<HTMLInputElement>('.issue-list .issue-item input[type="checkbox"]');
    issueCheckbox!.click();
    expect(issueCheckbox!.checked).toBe(true);
  });

  it('hides a repairer via the breakdown filter', async () => {
    await mountRepair();
    await runScan();
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-filter')).not.toBeNull());
    const before = document.body.textContent;
    document.querySelector<HTMLButtonElement>('.breakdown-filter')!.click();
    await vi.waitFor(() => expect(document.querySelector('tr.repairer-hidden')).not.toBeNull());
    expect(document.body.textContent).not.toBe(before);
  });

  it('shows an error when the scan endpoint fails', async () => {
    const routes = defaultRoutes().filter((r) => !(r.method === 'POST' && String(r.match).includes('scan')));
    routes.push({ method: 'POST', match: /\/scan$/, respond: () => jsonResponse({ message: 'scan boom' }, 500) });
    await mountRepair(routes);
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.error-message')).not.toBeNull());
    expect(document.querySelector('.error-message')!.textContent).toContain('scan boom');
  });

  it('surfaces an error when the repair endpoint fails', async () => {
    const routes = defaultRoutes().filter((r) => !(r.method === 'POST' && String(r.match).includes('repair')));
    routes.push({ method: 'POST', match: /\/repair$/, respond: () => jsonResponse({ message: 'repair boom' }, 500) });
    await mountRepair(routes);
    await runScan();
    document.querySelector<HTMLInputElement>('.issues-table thead .col-checkbox input')!.click();
    const repairBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').startsWith('Repair'),
    )!;
    await vi.waitFor(() => expect(repairBtn.disabled).toBe(false));
    repairBtn.click();
    await vi.waitFor(() => expect(document.querySelector('.error-message')).not.toBeNull());
    expect(document.querySelector('.error-message')!.textContent).toContain('repair boom');
  });

  it('blocks a scan and explains when the project id is missing from the URL', async () => {
    await mountRepair(defaultRoutes(), '?feature=repair');
    textButton('Scan').click();
    await vi.waitFor(() => expect(document.querySelector('.error-message')).not.toBeNull());
    expect(document.querySelector('.error-message')!.textContent).toContain('Project ID is missing');
  });

  it('reloads repairers when the entity type changes', async () => {
    await mountRepair();
    const select = document.querySelector<HTMLSelectElement>('.form-row select');
    expect(select).not.toBeNull();
    select!.value = 'DOCUMENT';
    select!.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => {
      const calls = fetchMock.mock.calls.filter((c) => String(c[0]).includes('entityType=DOCUMENT'));
      expect(calls.length).toBeGreaterThan(0);
    });
  });

  it('toggles all repairers on and off from the Select All checkbox', async () => {
    await mountRepair();
    const selectAll = Array.from(document.querySelectorAll<HTMLInputElement>('.repairer-item.select-all input'))[0];
    expect(selectAll).toBeTruthy();
    selectAll.click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('(2/2 selected)'));
    selectAll.click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('(0/2 selected)'));
  });

  it('toggles the option checkbox of a selected repairer', async () => {
    await mountRepair();
    // The default-selected repairer exposes a boolean config; its setting row is a .repairer-setting.
    const settingCheckbox = document.querySelector<HTMLInputElement>('.repairer-setting input[type="checkbox"]');
    expect(settingCheckbox).not.toBeNull();
    const before = settingCheckbox!.checked;
    settingCheckbox!.click();
    await vi.waitFor(() => expect(settingCheckbox!.checked).toBe(!before));
  });

  it('edits the advanced scan parameters (limit, timeout, sort, revision, hide-valid)', async () => {
    await mountRepair();
    (document.querySelector('.advanced-section summary') as HTMLElement).click();
    await vi.waitFor(() => expect(document.querySelector('#hide-valid')).not.toBeNull());

    const numberInputs = () =>
      Array.from(document.querySelectorAll<HTMLInputElement>('.advanced-fields input[inputmode="numeric"]'));
    // Two NumericInputs (Show Top Rows = 100, Scan time limit = 60) plus the revision SearchableInput.
    const limitInput = numberInputs().find((i) => i.value === '100')!;
    expect(limitInput).toBeTruthy();
    await userEvent.fill(limitInput, '5');
    expect(limitInput.value).toBe('5');
    // Clear -> 0 -> blank; blur restores the default (100); Enter on empty also restores it.
    await userEvent.clear(limitInput);
    await userEvent.keyboard('{Enter}');
    limitInput.blur();
    await vi.waitFor(() => expect(limitInput.value).toBe('100'));

    const timeoutInput = numberInputs().find((i) => i.value === '60')!;
    await userEvent.fill(timeoutInput, '30');
    expect(timeoutInput.value).toBe('30');

    const sortInput = Array.from(
      document.querySelectorAll<HTMLInputElement>('.advanced-fields input[type="text"]'),
    ).find((i) => i.value === '~updated');
    if (sortInput) {
      await userEvent.fill(sortInput, 'created');
      expect(sortInput.value).toBe('created');
    }

    // Revision is an editable numeric SearchableInput (placeholder HEAD); typing drives its value sync.
    const revInput = Array.from(document.querySelectorAll<HTMLInputElement>('.advanced-fields input')).find(
      (i) => i.placeholder === 'HEAD' && !i.readOnly,
    );
    if (revInput) {
      await userEvent.fill(revInput, '4321');
      expect(revInput.value).toBe('4321');
    }

    const hideValid = document.querySelector<HTMLInputElement>('#hide-valid')!;
    hideValid.click();
    expect(hideValid.checked).toBe(true);
  });

  it('scans when Enter is pressed in the query field', async () => {
    await mountRepair();
    // The Query text input (not the readonly .sd-trigger of the entity dropdown).
    const query = Array.from(document.querySelectorAll<HTMLInputElement>('.form-row input[type="text"]')).find(
      (i) => !i.readOnly && !i.classList.contains('sd-trigger'),
    )!;
    await userEvent.fill(query, 'id:EL-100');
    await userEvent.keyboard('{Enter}');
    await vi.waitFor(() => expect(document.querySelector('.results-section')).not.toBeNull());
  });

  it('shows "No issues found" for an empty scan result', async () => {
    const routes = defaultRoutes().filter((r) => !(r.method === 'POST' && String(r.match).includes('scan')));
    routes.push({ method: 'POST', match: /\/scan$/, json: { items: [], report: '' } });
    await mountRepair(routes);
    await runScan();
    await vi.waitFor(() => expect(document.querySelector('.no-issues')).not.toBeNull());
  });

  it('annotates issues with success, warnings and failures after a partial repair', async () => {
    const routes = defaultRoutes().filter((r) => !(r.method === 'POST' && String(r.match).includes('repair')));
    routes.push({
      method: 'POST',
      match: /\/repair$/,
      respond: (_url, init) => {
        const body = JSON.parse(String(init?.body));
        return jsonResponse(
          (body.issueMetaInfos as string[]).map((m) => {
            if (m === 'meta-1') return { issueMetaInfo: m, success: false, warnings: [] };
            if (m === 'meta-2') return { issueMetaInfo: m, success: true, warnings: ['heads up'] };
            return { issueMetaInfo: m, success: true, warnings: [] };
          }),
        );
      },
    });
    await mountRepair(routes);
    await runScan();

    // Expand EL-100 (has meta-1 + meta-2) and select its issues via the row checkbox.
    const rows = Array.from(document.querySelectorAll<HTMLTableRowElement>('.issues-table tbody tr'));
    const elRow = rows.find((r) => (r.textContent ?? '').includes('EL-100'))!;
    elRow.querySelector<HTMLElement>('.col-issues.clickable')!.click();
    await vi.waitFor(() => expect(document.querySelector('.issue-list')).not.toBeNull());
    elRow.querySelector<HTMLInputElement>('.col-checkbox input[type="checkbox"]')!.click();

    const repairBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.textContent ?? '').startsWith('Repair'),
    )!;
    await vi.waitFor(() => expect(repairBtn.disabled).toBe(false));
    repairBtn.click();

    // A failed issue and a succeeded-with-warning issue both render.
    await vi.waitFor(() => expect(document.querySelector('.issue-item.issue-failed')).not.toBeNull());
    expect(document.querySelector('.issue-item.issue-success')).not.toBeNull();
    expect(document.querySelector('.issue-warnings')).not.toBeNull();
  });

  it('reports an error when the repairers fail to load', async () => {
    const routes = defaultRoutes().filter((r) => !String(r.match).includes('repairers'));
    routes.push({ method: 'GET', match: /\/repairers/, respond: () => jsonResponse({ message: 'no repairers' }, 500) });
    fetchMock = installFetchMock(routes);
    setUrl('?feature=repair&projectId=elibrary');
    render(<App />);
    await vi.waitFor(() => expect(document.body.textContent).toContain('Failed to load repairers'));
  });

  it('reports an error when the baselines fail to load but still shows the page', async () => {
    const routes = defaultRoutes().filter((r) => !String(r.match).includes('baselines'));
    routes.push({ method: 'GET', match: /\/baselines/, respond: () => jsonResponse({ message: 'no baselines' }, 500) });
    await mountRepair(routes);
    await vi.waitFor(() => expect(document.body.textContent).toContain('Failed to load baselines'));
  });

  it('scans with "show items with issues only" enabled and still filters on hidden repairers', async () => {
    await mountRepair();
    (document.querySelector('.advanced-section summary') as HTMLElement).click();
    await vi.waitFor(() => expect(document.querySelector('#hide-valid')).not.toBeNull());
    document.querySelector<HTMLInputElement>('#hide-valid')!.click();
    await runScan();
    // Open the breakdown and hide a repairer; the visibility filters run with hideValidAtScanTime=true.
    document.querySelector<HTMLButtonElement>('.breakdown-toggle')!.click();
    await vi.waitFor(() => expect(document.querySelector('.breakdown-filter')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('.breakdown-filter')!.click();
    await vi.waitFor(() => expect(document.querySelector('tr.repairer-hidden')).not.toBeNull());
  });

  it('expands a collection and its sub-items, and selects the collection', async () => {
    await mountRepair();
    await runScan();
    // Find the collection row (COLL-1) and expand it via its issues cell.
    const rows = Array.from(document.querySelectorAll<HTMLTableRowElement>('.issues-table tbody tr'));
    const collRow = rows.find((r) => (r.textContent ?? '').includes('COLL-1'))!;
    expect(collRow).toBeTruthy();
    collRow.querySelector<HTMLElement>('.col-issues.clickable')!.click();
    // Sub-items DOC-1 / DOC-2 become visible.
    await vi.waitFor(() => expect(document.body.textContent).toContain('DOC-1'));
    expect(document.body.textContent).toContain('DOC-2');

    // Expand a sub-item to reveal its issue list.
    const subRow = Array.from(document.querySelectorAll<HTMLTableRowElement>('.subitem-row')).find((r) =>
      (r.textContent ?? '').includes('DOC-1'),
    )!;
    subRow.querySelector<HTMLElement>('.col-issues.clickable')!.click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('Bad enum in DOC-1'));

    // Select the whole collection via its row checkbox (toggleCollectionSelection).
    const collCheckbox = collRow.querySelector<HTMLInputElement>('.col-checkbox input[type="checkbox"]');
    expect(collCheckbox).not.toBeNull();
    collCheckbox!.click();
    await vi.waitFor(() => {
      const repairBtn = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
        (b.textContent ?? '').startsWith('Repair'),
      );
      expect(repairBtn && !repairBtn.disabled).toBe(true);
    });
  });
});
