import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { GENERAL_CHECKS, PURGE_OUTDATED_DATA } from '../src/navigation';
import Home, { localHref, subTopicHref } from '../src/pages/Home';
import { navigateSelf, navigateShell } from '../src/services/shell';

// The entry page of the XML-Repair navigation node. Its job is to reach the two nodes below it, which it
// does by appending a node id to the portal shell's own topic path.

// Both navigations are mocked at the module seam: `window.top` cannot be redefined here, and
// `window.location.assign` cannot be spied on, so this is the only way to observe which one the page chose
// without navigating the test runner. navigateShell's own behavior is covered in shell.test.ts.
vi.mock('../src/services/shell', () => ({ navigateShell: vi.fn(), navigateSelf: vi.fn() }));
const navigateShellMock = vi.mocked(navigateShell);
const navigateSelfMock = vi.mocked(navigateSelf);

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const linkButton = (label: string): HTMLButtonElement => {
  const b = Array.from(document.querySelectorAll<HTMLButtonElement>('.link-button')).find(
    (x) => (x.textContent ?? '').trim() === label,
  );
  if (!b) throw new Error(`link "${label}" not found`);
  return b;
};

const renderHome = async () => {
  render(<Home />);
  await vi.waitFor(() => expect(document.querySelector('.home-page')).not.toBeNull());
};

beforeEach(() => {
  // mockReturnValue does not clear the call history, and these are module-level mocks shared by every test.
  navigateShellMock.mockReset();
  navigateSelfMock.mockReset();
  navigateShellMock.mockReturnValue(false);
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
    await renderHome();

    expect(linkButton('General checks')).toBeDefined();
    expect(linkButton('Purge outdated data')).toBeDefined();
    expect(document.body.textContent).toContain('Please select below what you wish to do');
  });

  it('navigates the portal shell to the sub-node when it can be driven', async () => {
    navigateShellMock.mockReturnValue(true);
    await renderHome();

    linkButton('Purge outdated data').click();

    expect(navigateShellMock).toHaveBeenCalledOnce();
    expect(navigateSelfMock).not.toHaveBeenCalled();
    // The page hands over how to build the target, not the target itself, so the shell URL stays unread here.
    const buildHref = navigateShellMock.mock.calls[0][0];
    expect(buildHref('https://polarion/#/project/elibrary/xml-repair')).toBe(
      'https://polarion/#/project/elibrary/xml-repair/purge-outdated-data',
    );
  });

  it('falls back to its own feature router when the shell cannot be driven', async () => {
    // No separate top window, or a cross-origin one: both report false, and the click must still land somewhere
    // rather than dying inside the handler.
    setUrl('?feature=home&projectId=elibrary');
    await renderHome();

    linkButton('General checks').click();

    expect(navigateSelfMock).toHaveBeenCalledWith('?feature=general-checks&projectId=elibrary');
  });

  it('builds the local href from the current query, keeping the project', () => {
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
