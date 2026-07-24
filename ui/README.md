# XML Repair App (React UI)

This submodule contains the React frontend for the XML Repair Polarion extension, built on the shared
`@grigoriev/react-sbb-polarion` (RSP) component library. It is a single Vite bundle
with feature routing by `?feature=<id>`, hosting three surfaces:

- **Scan & Repair** (no `?feature=`, the default): scan and repair XML issues in Polarion entities
  (Work Items, Documents, Baseline Collections). Opened by `XmlRepairNavigationExtender`.
- **About** (`?feature=about`): the shared RSP About page.
- **Repair Authorization** (`?feature=authorization`): configure which global/project roles may repair.

The app is built with Vite and React, producing a static bundle that gets embedded into the extension JAR during the Maven build.

## How it integrates with Polarion

1. **Navigation entry point** — `XmlRepairNavigationExtender` registers an "XML-Repair" item in Polarion's left side panel. Clicking it loads `/polarion/xml-repair-app/ui/app/index.html`.

2. **Webapp registration** — `plugin.xml` declares a `xml-repair-app` webapp. Polarion's Tomcat serves the static files through `XmlRepairAppServlet` (mapped to `/ui/*`).

3. **REST communication** — The React app calls the existing REST API at `/polarion/xml-repair/rest/internal/*` (or `/api/*` with a bearer token) to list repairers, run scans, and execute repairs.

4. **Build pipeline** — During `mvn package`, the `frontend-maven-plugin` runs `npm install` and `npm run build` inside this folder. The `maven-resources-plugin` then copies `ui/dist/app/` into `src/main/resources/webapp/xml-repair-app/app/`, so it ends up in the final JAR.

## Local development

Prerequisites: Node.js 20+ installed.

```bash
# Install dependencies
npm install

# Start dev server with hot reload
npm run dev
```

By default the dev server runs on `http://localhost:5173`. The app requires a `projectId` query parameter, so open it as:

```
http://localhost:5173/?projectId=elibrary
```

To proxy requests to a running Polarion instance, create a `.env.local` file:

```env
VITE_BASE_URL=https://your-polarion-host
```

The Vite dev server will forward `/polarion/xml-repair/rest/*` calls to that URL (configured in `vite.config.js`).

To use bearer token authentication instead of Polarion's session-based auth, also set:

```env
VITE_BEARER_TOKEN=your-personal-access-token
```

### Code formatting

This project uses [Prettier](https://prettier.io/) for consistent code formatting. The configuration is in `.prettierrc`.

```bash
# Format all source files
npm run format

# Check formatting without writing (useful in CI)
npm run format:check
```

#### IntelliJ IDEA setup

1. Go to **Settings > Plugins**, install the **Prettier** plugin if not already installed.
2. Go to **Settings > Languages & Frameworks > JavaScript > Prettier**.
3. Set **Prettier package** to `~/ui/node_modules/prettier` (or let IDEA auto-detect it).
4. Check **On 'Reformat Code' action**.
5. Check **On save** (optional, for auto-format on save).
6. In the **Run for files** field, ensure it includes: `{**/*.ts,**/*.tsx,**/*.css,**/*.html}`

Now `Ctrl+Alt+L` (Reformat Code) will use Prettier instead of the built-in formatter.

#### VS Code setup

1. Install the **Prettier - Code formatter** extension (`esbenp.prettier-vscode`).
2. Open **Settings** (`Ctrl+,`) and set:
   - **Editor: Default Formatter** to `Prettier - Code formatter`
   - **Editor: Format On Save** to `true`
3. Alternatively, add a `.vscode/settings.json` in the `ui/` folder:

```json
{
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.formatOnSave": true
}
```

### Other commands

```bash
# Production build (outputs to dist/app/)
npm run build

# Preview the production build locally
npm run preview
```

### Testing & quality

Tests use Vitest in browser mode (real Chromium via Playwright); REST is mocked at the `fetch`
boundary, so no Polarion is needed. Visual-regression pixels only match inside the pinned Playwright
Docker image, so references are generated and checked there (Windows is a dev environment only).

```bash
# Behavior suite + the 80% istanbul coverage gate (runs anywhere; excludes visual tests)
npm run test:coverage

# Full suite (behavior + visual regression) inside the pinned Playwright Docker image (authoritative)
npm run test:docker

# Regenerate the committed visual reference PNGs (Docker only) after an intentional UI change
npm run test:update:docker

# Lint
npm run lint          # eslint .
npm run lint:fix
```

The repo-root pre-commit hooks run `format:check`, `lint`, and the dockerized coverage suite on `ui/`
changes; `mvn install` runs `test:docker` in the `test` phase (skip with `-DskipJsTests` on a
Docker-less host).
