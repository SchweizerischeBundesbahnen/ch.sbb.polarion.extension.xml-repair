// Runs before every test file (see vitest.config.ts setupFiles).
//
// Load the same stylesheets the app renders with so the browser paints components realistically:
//   1. react-sbb-polarion's bundled control CSS (tokens + buttons/inputs/checkboxes/searchable-dropdown/
//      alerts + the shared component styles), the same import main.tsx uses.
//   2. this app's own App.css.
//   3. the vendored generic data-table look (src/generic/tables.css) - the Scan & Repair results and
//      breakdown tables use `.sbb-table`, which RSP tokenizes but does not lay out.
// The Polarion-served stylesheets linked in index.html (presentation.css, github-markdown-light.css)
// are baseline chrome / help-article styling and are not loaded here. Also registers jest-dom matchers.
import '@grigoriev/react-sbb-polarion/style.css';
import '@testing-library/jest-dom/vitest';
import '../src/App.css';
import '../src/generic/tables.css';
