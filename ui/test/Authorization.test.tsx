import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { type FetchMock, type Route, installFetchMock } from './mockFetch';

// Behavior test of the Repair Authorization page, driven through the real App (feature router).
//
// The page is react-sbb-polarion's AuthorizationSettings, which has its own suite there - the role
// dropdowns, the confirmations, the revision table. What is this extension's own, and therefore what is
// tested here, is the wiring: the `authorization` setting the roles are written to, and the
// `globalRoles` / `projectRoles` payload that generic's AuthorizationModel - and through it
// `XmlRepairPolarionService.userAuthorizedForRepair` - reads back.

const origUrl = window.location.pathname + window.location.search;
const SCOPE = 'project/elibrary/';
const CONTENT_URL = /\/settings\/authorization\/names\/Default\/content\?/;

const defaultRoutes = (): Route[] => [
  {
    method: 'GET',
    match: /\/roles\?/,
    json: { globalRoles: ['admin', 'user'], projectRoles: ['project_admin', 'project_user'] },
  },
  { method: 'GET', match: CONTENT_URL, json: { globalRoles: ['admin'], projectRoles: [] } },
  { method: 'PUT', match: CONTENT_URL, json: {} },
];

/** The native <select multiple> of one role group. RSP's SearchableSelect ids it and inserts its own
 *  widget right after it, so this is the handle for both the selection and the rendered chips. */
const rolesSelect = (group: 'global' | 'project'): HTMLSelectElement => {
  const select = document.querySelector<HTMLSelectElement>(`select#${group}-roles`);
  if (!select) {
    throw new Error(`no ${group} roles control`);
  }
  return select;
};

/** The roles one group shows as granted, as the chips its dropdown trigger paints. */
const grantedRoles = (group: 'global' | 'project'): string[] =>
  Array.from(rolesSelect(group).nextElementSibling!.querySelectorAll('.sd-chip-label')).map((chip) =>
    (chip.textContent ?? '').trim(),
  );

/** Grants exactly these roles in one group, the way a click on the dropdown's options ends up doing,
 *  then waits for the chips to follow. That wait is what proves React took the change, so a Save right
 *  after reads the new selection rather than the previous render's. */
async function grantRoles(group: 'global' | 'project', ...roles: string[]) {
  const select = rolesSelect(group);
  for (const option of Array.from(select.options)) {
    option.selected = roles.includes(option.value);
  }
  select.dispatchEvent(new Event('change', { bubbles: true }));
  await vi.waitFor(() => expect(grantedRoles(group)).toEqual(roles));
}

const saveButton = (): HTMLButtonElement => {
  const button = Array.from(document.querySelectorAll<HTMLButtonElement>('.action-buttons button')).find(
    (b) => (b.textContent ?? '').trim() === 'Save',
  );
  if (!button) {
    throw new Error('Save button not found');
  }
  return button;
};

/** The body of the single PUT the page sends, once it has been sent. */
async function savedContent(fetchMock: FetchMock): Promise<{ url: string; body: unknown }> {
  const puts = () => fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT');
  await vi.waitFor(() => expect(puts()).toHaveLength(1));
  const [url, init] = puts()[0];
  return { url: String(url), body: JSON.parse(String((init as RequestInit).body)) };
}

async function renderPage(fetchMock: FetchMock = installFetchMock(defaultRoutes())) {
  window.history.replaceState({}, '', `?feature=authorization&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  render(<App />);
  // Both controls, not just the first: they are upgraded asynchronously, and driving the one that is
  // already there while the other is still a bare <select> reads a selection the page has not seen.
  await vi.waitFor(() => expect(document.querySelectorAll('.roles-group .sd-trigger-multi')).toHaveLength(2));
  return fetchMock;
}

afterEach(() => {
  cleanup();
  document.querySelectorAll('.sd-portal').forEach((portal) => portal.remove());
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

describe('Repair Authorization page', () => {
  it('shows the roles the setting grants', async () => {
    await renderPage();

    expect(grantedRoles('global')).toEqual(['admin']);
    expect(grantedRoles('project')).toEqual([]);
  });

  it('saves the roles picked in both groups under the authorization setting', async () => {
    const fetchMock = await renderPage();

    await grantRoles('global', 'admin', 'user');
    await grantRoles('project', 'project_admin');
    saveButton().click();

    const { url, body } = await savedContent(fetchMock);
    expect(url).toContain('/settings/authorization/names/Default/content');
    expect(url).toContain(`scope=${encodeURIComponent(SCOPE)}`);
    expect(body).toEqual({ globalRoles: ['admin', 'user'], projectRoles: ['project_admin'] });
  });

  it('saves empty role lists when the granted roles are cleared', async () => {
    // The case that locks repair down to the global admin fallback, so the empty lists have to survive
    // serialization as lists rather than as a missing or one-empty-string field.
    const fetchMock = await renderPage();

    await grantRoles('global');
    saveButton().click();

    const { body } = await savedContent(fetchMock);
    expect(body).toEqual({ globalRoles: [], projectRoles: [] });
  });
});
