/**
 * Navigating the Polarion portal window this page is embedded in.
 *
 * In Polarion every surface of this app runs inside an iframe of the portal shell, whose URL is the topic path
 * a navigation page has to append to. Two things can stop that: there is no separate top window at all
 * (`vite dev`, a test), or the shell is cross-origin.
 *
 * Only the second case needs a try/catch, and it has to sit around the `location` access rather than around the
 * window lookup: reading `window.top` and comparing it to `window.self` are not restricted operations and never
 * throw. A cross-origin `Location` exposes only the `href` setter and `replace()`, so reading
 * `shell.location.href` - which building the topic path requires - throws a SecurityError, and so does calling
 * `assign()`. Both are reported the same way, by returning false, so the caller can fall back.
 */
export function navigateShell(
  buildHref: (shellHref: string) => string,
  // Injectable so a test can supply a shell that throws the way a cross-origin one does; `window.top` is not
  // redefinable, so that failure mode is otherwise unreachable in a test.
  shell: Window | null = window.top,
): boolean {
  if (!shell || shell === window.self) {
    return false;
  }

  let shellHref: string;
  try {
    shellHref = shell.location.href;
  } catch {
    return false;
  }

  // Deliberately outside both guards: a bug in the caller's own path building must surface, not be reported as
  // an undrivable shell.
  const target = buildHref(shellHref);

  try {
    shell.location.assign(target);
    return true;
  } catch {
    return false;
  }
}

/** Navigates this page itself, the fallback when the shell cannot be driven. */
export function navigateSelf(href: string): void {
  window.location.assign(href);
}
