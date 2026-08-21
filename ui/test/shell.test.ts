import { describe, expect, it, vi } from 'vitest';
import { navigateShell } from '../src/services/shell';

// Driving the Polarion portal window. The shell is passed in here, which is the whole reason the parameter
// exists: `window.top` cannot be redefined in this environment, so a cross-origin shell - the one failure that
// actually matters - is otherwise unreachable in a test.

const fakeShell = (assign: (href: string) => void, href = 'https://polarion/#/project/elibrary/xml-repair') =>
  ({ location: { href, assign } }) as unknown as Window;

const securityError = () => {
  throw new DOMException('Blocked a frame from accessing a cross-origin frame.', 'SecurityError');
};

describe('navigateShell', () => {
  it('drives the shell to the built href and reports success', () => {
    const assign = vi.fn();

    expect(navigateShell((href) => `${href}/general-checks`, fakeShell(assign))).toBe(true);
    expect(assign).toHaveBeenCalledWith('https://polarion/#/project/elibrary/xml-repair/general-checks');
  });

  it('reports failure when there is no separate top window', () => {
    // `vite dev` and the tests run the page at the top level, so there is no portal to drive.
    expect(navigateShell((href) => href, null)).toBe(false);
    expect(navigateShell((href) => href, window.self)).toBe(false);
  });

  it('reports failure when reading the cross-origin shell URL throws', () => {
    // A cross-origin Location exposes only the href setter and replace(), so the getter throws. Reading
    // window.top and comparing it to window.self do not, which is why the guard sits here and not there.
    const crossOrigin = {
      get location(): Location {
        return {
          get href(): string {
            return securityError();
          },
          assign: () => undefined,
        } as unknown as Location;
      },
    } as unknown as Window;

    expect(navigateShell((href) => href, crossOrigin)).toBe(false);
  });

  it('reports failure when assigning to the cross-origin shell throws', () => {
    // assign() is not on the cross-origin allowlist either, so a readable href is no guarantee.
    expect(navigateShell((href) => href, fakeShell(securityError))).toBe(false);
  });

  it('does not swallow a failure of the href builder it was given', () => {
    // Only the shell access is guarded; a bug in building the path must not be reported as an undrivable shell.
    expect(() => navigateShell(() => securityError(), fakeShell(vi.fn()))).toThrow();
  });
});
