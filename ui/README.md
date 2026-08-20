# XML Repair App (React UI)

This submodule contains the React frontend for the XML Repair Polarion extension, built on the shared
`@sbb-polarion/react-sbb-polarion` (RSP) component library. It is a single Vite bundle
with feature routing by `?feature=<id>`, hosting five surfaces:

- **Home** (`?feature=home`): the entry page of the navigation node, linking to the two pages below it.
- **General checks** (`?feature=general-checks`): scan and repair XML issues in Polarion entities
  (Work Items, Documents, Baseline Collections). `?feature=repair` still resolves here, for older bookmarks.
- **Purge outdated data** (`?feature=purge-outdated-data`): find attributes which are filled on the scanned
  entities but no longer defined in their custom fields configuration, and clear the selected ones. It scans
  with `OutdatedCustomFieldsRepairer` alone and writes through the same `/repair` endpoint.
- **About** (`?feature=about`): the shared RSP About page.
- **Repair Authorization** (`?feature=authorization`): configure which global/project roles may repair.
  The shared RSP `AuthorizationSettings` page over this extension's `authorization` setting.

The first three ids are a contract with the Java side: `XmlRepairNavigationExtender` and its two
`NavigationExtenderNode`s put their own ids into `?feature=`, and the Home page appends a node id to the
portal's topic path to select it in the tree. `src/navigation.ts` holds them, and `test/navigation.test.ts`
pins the literals from this side while the Java tests pin them from the other.

The app is built with Vite and React, producing a static bundle that gets embedded into the extension JAR during the Maven build.

## How it integrates with Polarion

1. **Navigation entry point** — `XmlRepairNavigationExtender` registers an "XML-Repair" item in Polarion's left side panel, with `GeneralChecksNode` and `PurgeOutdatedDataNode` below it. Each one loads `/polarion/xml-repair-app/ui/app/index.html` with its own `?feature=`. `BreadcrumbTopic` relabels the shell's app header per page, which RSP's own `BreadcrumbInjector` cannot do for a node that has children.

2. **Webapp registration** — `plugin.xml` declares a `xml-repair-app` webapp. Polarion's Tomcat serves the static files through `XmlRepairAppServlet` (mapped to `/ui/*`).

3. **REST communication** — The React app calls the existing REST API at `/polarion/xml-repair/rest/internal/*` (or `/api/*` with a bearer token) to list repairers, run scans, and execute repairs. The authorization page additionally uses the settings endpoints and `/roles`; both come from the generic parent, and the role endpoints are opt-in, registered in `XmlRepairRestApplication`.

4. **Build pipeline** — During `mvn package`, the `frontend-maven-plugin` runs `npm ci` and `npm run build` inside this folder. The `maven-resources-plugin` then copies `ui/dist/app/` into `src/main/resources/webapp/xml-repair-app/app/`, so it ends up in the final JAR. `ci`, not `install`: the packaged bundle must come from the committed `package-lock.json`, the same graph the tests run against — so a `package.json` edit that is not reflected in the lock fails the build instead of being silently repaired. Locally you still use `npm install` (below), which is what updates the lock.

## Local development

Prerequisites: Node.js 24+ installed, matching the `engines` field of `package.json`. The Maven build
brings its own Node (v24.18.0, pinned by the generic parent), so this applies to running npm directly.

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

# Full suite (behavior + visual regression) + the coverage gate, inside the pinned image (canonical)
npm run test:coverage:docker

# Regenerate the committed visual reference PNGs (Docker only) after an intentional UI change
npm run test:update:docker

# Lint
npm run lint          # eslint .
npm run lint:fix
```

The repo-root pre-commit hooks run `format:check`, `lint`, and the dockerized coverage suite on `ui/`
changes; `mvn install` runs `test:coverage:docker` in the `test` phase (skip with `-DskipJsTests` on a
Docker-less host).

### Running the tests

**One command, locally and in CI: `npm run test:coverage:docker`.** It runs the full suite (behavior +
visual regression) plus the 80% istanbul coverage gate inside the pinned Playwright Docker image, which
is what the Maven `test` phase and the pre-commit hook execute. Docker must be running.

```bash
npm run test:coverage:docker   # the canonical run: full suite + coverage gate, in the pinned image
npm run test:coverage          # fast local loop: behavior only + the gate, no Docker, no pixels
npm run test:update:docker     # regenerate the committed reference PNGs after an intentional UI change
```

> `npm run test:coverage:full` is the inner command the Docker wrapper invokes. Run outside a container
> it is green, but it proves less than it looks: the reference screenshots are pixel-locked to the
> pinned image, so the visual suites detect that they are not in the reference environment and **skip
> themselves** rather than failing on the host's font metrics. It therefore reports the behavior suite
> and the coverage gate only - which is exactly what the `-DjsTestsNoDocker` Maven profile needs on a
> Docker-less host. To check the screenshots, use `test:coverage:docker`.
