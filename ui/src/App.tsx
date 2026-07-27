import { BreadcrumbInjector, Toaster } from '@grigoriev/react-sbb-polarion';
import { findFeature } from './features';
import Landing from './pages/Landing';

/**
 * Top-level feature router. There is a single index.html / bundle; the page to show is chosen from the
 * `feature` query parameter, e.g. `?feature=repair`. Every product surface has an explicit feature id:
 * XmlRepairNavigationExtender opens Scan & Repair as `?feature=repair&projectId=<id>` and hivemodule.xml
 * points the admin extenders at `?feature=about` / `?feature=authorization`.
 *
 * A missing or unknown feature (including the bare root `/`) renders the dev-only Landing stub - a scope
 * picker plus links to every feature - so the whole app can be exercised in `vite dev` without Polarion.
 */
export default function App() {
  const feature = new URLSearchParams(window.location.search).get('feature');
  const Page = findFeature(feature)?.component ?? Landing;

  return (
    // `.app` supplies the base font/padding (App.css); `standard-admin-page` scopes the shared generic
    // checkbox styling (bundled in react-sbb-polarion's style.css) and the --sbb-* control tokens.
    <div className="app standard-admin-page">
      {/* Fixes the app-header breadcrumb when opened as a project-navigation topic (nav extender). */}
      <BreadcrumbInjector
        marker="xml-repair"
        title="XML-Repair"
        icon="/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg"
      />
      {/* App-wide toast host: the shared react-sbb-polarion Toaster (top-center + richColors, so
          success toasts are green, errors red). Toasts are fired with `toast()` from sonner. */}
      <Toaster />
      <Page />
    </div>
  );
}
