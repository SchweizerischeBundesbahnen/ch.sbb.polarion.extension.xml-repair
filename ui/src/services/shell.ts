/**
 * The Polarion portal window this page is embedded in, or null when there is none to drive.
 *
 * In Polarion every surface of this app runs inside an iframe of the portal shell, whose URL is the topic path
 * the Home page navigates. Standing alone - `vite dev`, a test - there is no shell, and a cross-origin one
 * cannot be touched at all; both are "no shell", so callers fall back rather than reaching for `window.top`
 * defensively at each site. Being a module also makes it mockable, which `window.top` is not.
 */
export function getShellWindow(): Window | null {
  try {
    const shell = window.top;
    return shell && shell !== window.self ? shell : null;
  } catch {
    return null;
  }
}
