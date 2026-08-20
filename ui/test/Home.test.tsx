import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { GENERAL_CHECKS, PURGE_OUTDATED_DATA } from '../src/navigation';
import Home, { localHref, subTopicHref } from '../src/pages/Home';
import { getShellWindow } from '../src/services/shell';

// `window.top` is not redefinable, so the shell lookup is mocked at its own module seam instead.
vi.mock('../src/services/shell', () => ({ getShellWindow: vi.fn() }));
const shellMock = vi.mocked(getShellWindow);

// The entry page of the XML-Repair navigation node. Its job is to reach the two nodes below it, which it
// does by appending a node id to the portal shell's own topic path.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const linkButton = (label: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('.link-button')).find(
    (x) => (x.textContent ?? '').trim() === label,
  );
  if (!b) throw new Error(`link "${label}" not found`);
  return b;
};

beforeEach(() => {
  shellMock.mockReturnValue(null);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setUrl(origUrl);
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

describe('subTopicHref', () => {
  it('appends the node id to the portal topic path', () => {
    expect(subTopicHref('https://polarion/#/project/elibrary/xml-repair', GENERAL_CHECKS)).toBe(
      'https://polarion/#/project/elibrary/xml-repair/general-checks',
    );
  });

  it('does not double the separator when the topic path already ends with one', () => {
    expect(subTopicHref('https://polarion/#/project/elibrary/xml-repair/', PURGE_OUTDATED_DATA)).toBe(
      'https://polarion/#/project/elibrary/xml-repair/purge-outdated-data',
    );
  });
});

describe('Home page', () => {
  it('offers one link per page below the node', async () => {
    render(<Home />);

    await vi.waitFor(() => expect(document.querySelector('.home-page')).not.toBeNull());
    expect(linkButton('General checks')).toBeDefined();
    expect(linkButton('Purge outdated data')).toBeDefined();
    expect(document.body.textContent).toContain('Please select below what you wish to do');
  });

  it('navigates the portal shell to the sub-node when embedded', async () => {
    const assign = vi.fn();
    const shell = { location: { href: 'https://polarion/#/project/elibrary/xml-repair', assign } };
    shellMock.mockReturnValue(shell as unknown as Window);
    render(<Home />);

    await vi.waitFor(() => expect(document.querySelector('.home-page')).not.toBeNull());
    linkButton('Purge outdated data').click();

    expect(assign).toHaveBeenCalledWith('https://polarion/#/project/elibrary/xml-repair/purge-outdated-data');
  });

  it('addresses its own feature router when there is no shell, keeping the project', () => {
    // Standing alone (vite dev, a test) or behind a cross-origin shell there is no portal to drive, so the
    // page below is addressed through this bundle's own feature router instead. Only the URL is asserted:
    // window.location.assign cannot be stubbed here, and letting the click run would navigate the runner.
    setUrl('?feature=home&projectId=elibrary');

    expect(localHref(GENERAL_CHECKS)).toBe('?feature=general-checks&projectId=elibrary');
    expect(localHref(PURGE_OUTDATED_DATA)).toBe('?feature=purge-outdated-data&projectId=elibrary');
  });

  it('is what ?feature=home renders', async () => {
    setUrl('?feature=home&projectId=elibrary');
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.home-page')).not.toBeNull());
    expect(linkButton('General checks')).toBeDefined();
  });
});
