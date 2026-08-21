import { BreadcrumbInjector, Toaster } from '@sbb-polarion/react-sbb-polarion';
import { findFeature } from './features';
import { EXTENSION_ICON, EXTENSION_LABEL } from './navigation';
import Landing from './pages/Landing';

/**
 * Top-level feature router. There is a single index.html / bundle; the page to show is chosen from the
 * `feature` query parameter, e.g. `?feature=general-checks`. Every product surface has an explicit feature id:
 * XmlRepairNavigationExtender opens the node's entry page as `?feature=home&projectId=<id>` and its two root
 * nodes open `?feature=general-checks` / `?feature=purge-outdated-data`, while hivemodule.xml points the admin
 * extenders at `?feature=about` / `?feature=authorization`.
 *
 * A missing or unknown feature (including the bare root `/`) renders the dev-only Landing stub - a scope
 * picker plus links to every feature - so the whole app can be exercised in `vite dev` without Polarion.
 */
export default function App() {
  const feature = findFeature(new URLSearchParams(window.location.search).get('feature'));
  const Page = feature?.component ?? Landing;

  return (
    // `.app` supplies the base font/padding (App.css); `standard-admin-page` scopes the shared generic
    // checkbox styling (bundled in react-sbb-polarion's style.css) and the --sbb-* control tokens.
    <div className="app standard-admin-page">
      {/* Fixes the app-header breadcrumb when opened as a project-navigation topic (nav extender). The two
          pages below the root node name themselves and pass the node's label as `parent`, so the breadcrumb
          reads "XML-Repair › General checks"; every other surface keeps the extension's own label alone. */}
      <BreadcrumbInjector
        marker="xml-repair"
        title={feature?.breadcrumbTitle ?? EXTENSION_LABEL}
        parent={feature?.breadcrumbParent}
        icon={feature?.breadcrumbIcon ?? EXTENSION_ICON}
      />
      {/* App-wide toast host: the shared react-sbb-polarion Toaster (top-center + richColors, so
          success toasts are green, errors red). Toasts are fired with `toast()` from sonner. */}
      <Toaster />
      <Page />
    </div>
  );
}
