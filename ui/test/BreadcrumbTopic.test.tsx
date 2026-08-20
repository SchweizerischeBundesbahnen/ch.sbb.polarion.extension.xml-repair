import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import BreadcrumbTopic from '../src/components/BreadcrumbTopic';
import { getShellWindow } from '../src/services/shell';

// Relabels the Polarion app-header breadcrumb through the shared generic BreadcrumbBridge. What matters here is
// that it re-labels on every mount, because one bundle serves several navigation nodes.

vi.mock('../src/services/shell', () => ({ getShellWindow: vi.fn() }));
const shellMock = vi.mocked(getShellWindow);

interface FakeShell {
  document: Document;
  SbbBreadcrumbBridge?: { install: ReturnType<typeof vi.fn> };
}

let shell: FakeShell;

/** A shell whose document is a detached one, so nothing lands in the test page's own head. */
function fakeShell(withBridge: boolean): FakeShell {
  const doc = document.implementation.createHTMLDocument('shell');
  const result: FakeShell = { document: doc };
  if (withBridge) {
    result.SbbBreadcrumbBridge = { install: vi.fn() };
  }
  return result;
}

const loaders = (): HTMLScriptElement[] =>
  Array.from(shell.document.querySelectorAll<HTMLScriptElement>('script#sbb-breadcrumb-bridge-loader'));

beforeEach(() => {
  shell = fakeShell(false);
  shellMock.mockReturnValue(shell as unknown as Window);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('BreadcrumbTopic', () => {
  it('injects the bridge loader carrying the title, parent and icon', async () => {
    render(<BreadcrumbTopic marker="xml-repair" title="Purge outdated data" parent="XML-Repair" icon="/icon.svg" />);

    await vi.waitFor(() => expect(loaders()).toHaveLength(1));
    const loader = loaders()[0];
    expect(loader.src).toContain('/polarion/xml-repair-app/ui/generic/js/modules/BreadcrumbBridge.js');
    expect(loader.dataset.marker).toBe('xml-repair');
    expect(loader.dataset.title).toBe('Purge outdated data');
    expect(loader.dataset.parent).toBe('XML-Repair');
    expect(loader.dataset.icon).toBe('/icon.svg');
  });

  it('leaves out the optional keys when the page has no parent or icon', async () => {
    render(<BreadcrumbTopic marker="xml-repair" title="XML-Repair" />);

    await vi.waitFor(() => expect(loaders()).toHaveLength(1));
    expect(loaders()[0].dataset.parent).toBeUndefined();
    expect(loaders()[0].dataset.icon).toBeUndefined();
  });

  it('re-labels through an already loaded bridge rather than injecting again', async () => {
    shell = fakeShell(true);
    shellMock.mockReturnValue(shell as unknown as Window);

    render(<BreadcrumbTopic marker="xml-repair" title="General checks" parent="XML-Repair" />);

    await vi.waitFor(() => expect(shell.SbbBreadcrumbBridge!.install).toHaveBeenCalled());
    expect(shell.SbbBreadcrumbBridge!.install).toHaveBeenCalledWith({
      marker: 'xml-repair',
      title: 'General checks',
      parent: 'XML-Repair',
    });
    expect(loaders()).toHaveLength(0);
  });

  it('replaces a stale loader, so the first page opened does not keep the header for the session', async () => {
    render(<BreadcrumbTopic marker="xml-repair" title="General checks" />);
    await vi.waitFor(() => expect(loaders()[0]?.dataset.title).toBe('General checks'));
    cleanup();

    render(<BreadcrumbTopic marker="xml-repair" title="Purge outdated data" />);

    await vi.waitFor(() => expect(loaders()[0]?.dataset.title).toBe('Purge outdated data'));
    expect(loaders()).toHaveLength(1);
  });

  it('does nothing when the page stands alone', async () => {
    shellMock.mockReturnValue(null);

    render(<BreadcrumbTopic marker="xml-repair" title="XML-Repair" />);

    await new Promise((resolve) => requestAnimationFrame(resolve));
    expect(loaders()).toHaveLength(0);
  });
});
