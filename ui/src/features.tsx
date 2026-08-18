import { type ComponentType, useMemo } from 'react';
import { AuthorizationSettings, createAuthorizationService } from '@sbb-polarion/react-sbb-polarion';
import About from './pages/About';
import Repair from './pages/Repair';
import useRemote from './services/useRemote';

/** The named-settings feature the repair permissions are stored under. */
const AUTHORIZATION_SETTING = 'authorization';

/**
 * Repair Authorization: react-sbb-polarion's shared role-checkbox page over this extension's setting.
 * The checked roles are the ones `XmlRepairPolarionService.userAuthorizedForRepair` accepts.
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

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>` and is
 * what `hivemodule.xml` / the navigation extender point at. Keep the ids stable and aligned with the
 * extender ids: `repair` (XmlRepairNavigationExtender), `about` and `authorization` (hivemodule.xml).
 * A URL that matches none of these falls back to the dev Landing (see App.tsx / findFeature).
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
}

export const FEATURES: Feature[] = [
  {
    id: 'repair',
    label: 'Scan & Repair',
    description: 'Scan Polarion entities for XML issues and repair them.',
    component: Repair,
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
  return FEATURES.find((f) => f.id === id);
}
