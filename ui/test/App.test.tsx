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

  // The breadcrumb component itself is react-sbb-polarion's, and tested there; what belongs here is the
  // wiring - which label and parent each feature hands it, since one bundle serves several navigation nodes.
  describe('app-header breadcrumb', () => {
    const loader = (): HTMLScriptElement | null =>
      window.top?.document.querySelector<HTMLScriptElement>('script[id$="-breadcrumb-bridge"]') ?? null;

    it('names the page and its parent node for a page below the root node', async () => {
      installFetchMock([{ method: 'GET', match: /\/repairers/, json: REPAIRERS }]);
      setUrl('?feature=general-checks&projectId=elibrary');
      render(<App />);

      await vi.waitFor(() => expect(loader()).not.toBeNull());
      expect(loader()!.dataset.marker).toBe('xml-repair');
      expect(loader()!.dataset.title).toBe('General checks');
      expect(loader()!.dataset.parent).toBe('XML-Repair');
      expect(loader()!.dataset.icon).toContain('general_checks.svg');
    });

    it('keeps the extension label with no parent everywhere else', async () => {
      setUrl('?feature=home&projectId=elibrary');
      render(<App />);

      await vi.waitFor(() => expect(loader()).not.toBeNull());
      expect(loader()!.dataset.title).toBe('XML-Repair');
      expect(loader()!.dataset.parent).toBeUndefined();
      expect(loader()!.dataset.icon).toContain('_parent.svg');
    });
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
