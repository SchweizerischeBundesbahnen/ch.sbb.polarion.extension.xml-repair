import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';

// Docker-only snapshot of the entry page of the XML-Repair navigation node: the two links to the pages
// below it.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', origUrl);
  window.top?.document.querySelectorAll('script[id$="-breadcrumb-bridge"]').forEach((s) => s.remove());
});

describe.skipIf(!__PIXEL_REFERENCES__)('Home page visual', () => {
  it('links to the pages below the node', async () => {
    // embedded=true mirrors how the navigation node opens the page in Polarion.
    window.history.replaceState({}, '', '?feature=home&projectId=elibrary&embedded=true');
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.home-page')).not.toBeNull());

    const app = document.querySelector('.app') as HTMLElement;
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await expect(page.elementLocator(app)).toMatchScreenshot('home');
  });
});
