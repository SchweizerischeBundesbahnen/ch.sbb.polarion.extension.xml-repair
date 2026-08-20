/**
 * The Polarion portal window this page is embedded in.
 *
 * Every surface of this app runs inside an iframe of the portal shell: the admin pages, and the navigation
 * topics whose shell URL is the topic path itself. Standing alone - `vite dev`, a test - there is no shell, and
 * a cross-origin one cannot be touched at all. Both cases are "no shell", so callers get null and fall back.
 */
export function getShellWindow(): Window | null {
  try {
    const shell = window.top;
    return shell && shell !== window.self ? shell : null;
  } catch {
    return null;
  }
}
