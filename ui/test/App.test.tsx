import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { REPAIRERS } from './fixtures';
import { installFetchMock } from './mockFetch';

// The feature router (App.tsx): the `?feature=` param picks the page (`repair` is the Scan & Repair
// surface, opened by the navigation extender as `?feature=repair&projectId=<id>`); a missing or unknown
// feature falls back to the dev Landing stub.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

describe('feature router', () => {
  it('renders the Scan & Repair page for ?feature=repair', async () => {
    installFetchMock([{ method: 'GET', match: /\/repairers/, json: REPAIRERS }]);
    setUrl('?feature=repair&projectId=elibrary');
    render(<App />);
    await vi.waitFor(() => expect(document.body.textContent).toContain('Enumeration fields: Invalid value'));
    expect(Array.from(document.querySelectorAll('button')).some((b) => b.textContent === 'Scan')).toBe(true);
  });

  it('opens the Repair Authorization page against the authorization setting', async () => {
    // The page itself is react-sbb-polarion's, and tested there; what belongs here is the wiring -
    // that this feature id reads the roles and the setting this extension's permission check uses.
    const seen: string[] = [];
    installFetchMock([
      { method: 'GET', match: /\/roles\?/, json: { globalRoles: ['admin'], projectRoles: [] } },
      {
        method: 'GET',
        match: /\/settings\/[^/]+\/names\/Default\/content\?/,
        respond: (url: string) => {
          seen.push(url);
          return new Response(JSON.stringify({ globalRoles: ['admin'], projectRoles: [] }), {
            headers: { 'Content-Type': 'application/json' },
          });
        },
      },
    ]);
    setUrl(`?feature=authorization&scope=${encodeURIComponent('project/elibrary/')}`);
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.roles-list')).not.toBeNull());
    expect(document.querySelector('h1')!.textContent).toBe('Repair Authorization');
    expect(seen.some((url) => url.includes('/settings/authorization/'))).toBe(true);
  });

  it('falls back to the dev Landing for an unknown feature', async () => {
    installFetchMock([{ method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: { data: [] } }]);
    setUrl('?feature=does-not-exist');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.feature-list')).not.toBeNull());
  });

  it('renders the dev Landing for a bare URL with no feature', async () => {
    installFetchMock([{ method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: { data: [] } }]);
    setUrl('?');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.feature-list')).not.toBeNull());
  });

  it('renders the dev Landing (with feature links) for ?feature=landing', async () => {
    installFetchMock([
      {
        method: 'GET',
        match: /\/polarion\/rest\/v1\/projects/,
        // Mixed shapes exercise the id/name fallbacks in the projects mapper.
        json: {
          data: [{ id: 'elibrary', attributes: { name: 'E-Library' } }, { attributes: { id: 'drivepilot' } }],
        },
      },
    ]);
    setUrl('?feature=landing');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.feature-list')).not.toBeNull());
    const links = Array.from(document.querySelectorAll('.feature-list a')).map((a) => a.textContent);
    expect(links).toContain('General checks');
    expect(links).toContain('Purge outdated data');
    expect(links).toContain('About');
    expect(links).toContain('Repair Authorization');
  });

  it('shows a friendly error on the Landing when projects cannot be loaded', async () => {
    installFetchMock([
      { method: 'GET', match: /\/polarion\/rest\/v1\/projects/, json: { message: 'nope' }, status: 500 },
    ]);
    setUrl('?feature=landing');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    expect(document.querySelector('.alert-error')!.textContent).toContain('Could not load projects');
  });
});
