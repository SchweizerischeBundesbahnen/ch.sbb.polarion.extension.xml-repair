import { type ComponentType, useMemo } from 'react';
import { AuthorizationSettings, createAuthorizationService } from '@sbb-polarion/react-sbb-polarion';
import { EXTENSION_LABEL, GENERAL_CHECKS, HOME, PURGE_OUTDATED_DATA } from './navigation';
import About from './pages/About';
import Home from './pages/Home';
import Purge from './pages/Purge';
import Repair from './pages/Repair';
import useRemote from './services/useRemote';

/** The named-settings feature the repair permissions are stored under. */
const AUTHORIZATION_SETTING = 'authorization';

/**
 * Repair Authorization: react-sbb-polarion's shared role-selection page over this extension's setting.
 * The selected roles are the ones `XmlRepairPolarionService.userAuthorizedForRepair` accepts.
 */
function Authorization() {
  const { sendRequest } = useRemote();
  const service = useMemo(() => createAuthorizationService(sendRequest, AUTHORIZATION_SETTING), [sendRequest]);
  return (
    <AuthorizationSettings
      title="Repair Authorization"
      service={service}
      quickHelp={
        <>
          <h3>Permissions</h3>
          <p>The finding issues functionality is unrestricted and available to all users.</p>
          <p>
            On the other hand, the repair functionality can be restricted or permitted for specific global or project
            roles.
          </p>
          <p>By default, only users with the global admin role have permission to repair.</p>
          <p>
            Additionally, project administrators can configure merging permissions based on the needs of their specific
            project, allowing for more granular control.
          </p>
        </>
      }
    />
  );
}

/** What `?feature=repair` used to open, kept working so older bookmarks still land on the right page. */
const LEGACY_FEATURE_IDS: Record<string, string> = { repair: GENERAL_CHECKS };

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>` and is
 * what `hivemodule.xml` / the navigation extender point at. Keep the ids stable and aligned with the
 * extender ids: `home`, `general-checks` and `purge-outdated-data` (XmlRepairNavigationExtender and its
 * root nodes), `about` and `authorization` (hivemodule.xml).
 * A URL that matches none of these falls back to the dev Landing (see App.tsx / findFeature).
 *
 * The three optional breadcrumb fields override what the app header shows. Only the pages that hang below the
 * root navigation node need them; everything else keeps the extension's own label.
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
  breadcrumbTitle?: string;
  breadcrumbParent?: string;
  breadcrumbIcon?: string;
}

export const FEATURES: Feature[] = [
  {
    id: HOME,
    label: 'XML-Repair',
    description: 'Entry page of the navigation node, linking to the pages below it.',
    component: Home,
  },
  {
    id: GENERAL_CHECKS,
    label: 'General checks',
    description: 'Scan Polarion entities for XML issues and repair them.',
    component: Repair,
    breadcrumbTitle: 'General checks',
    breadcrumbParent: EXTENSION_LABEL,
    breadcrumbIcon: '/polarion/xml-repair-app/ui/images/menu/16x16/general_checks.svg',
  },
  {
    id: PURGE_OUTDATED_DATA,
    label: 'Purge outdated data',
    description: 'Find attributes which are filled but no longer defined, and clear them.',
    component: Purge,
    breadcrumbTitle: 'Purge outdated data',
    breadcrumbParent: EXTENSION_LABEL,
    breadcrumbIcon: '/polarion/xml-repair-app/ui/images/menu/16x16/purge.svg',
  },
  {
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
  {
    id: 'authorization',
    label: 'Repair Authorization',
    description: 'Configure which global and project roles are allowed to repair.',
    component: Authorization,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  if (id === null) {
    return undefined;
  }
  const resolvedId = LEGACY_FEATURE_IDS[id] ?? id;
  return FEATURES.find((f) => f.id === resolvedId);
}
