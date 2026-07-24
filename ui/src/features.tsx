import type { ComponentType } from 'react';
import About from './pages/About';
import Authorization from './pages/Authorization';
import Repair from './pages/Repair';

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
