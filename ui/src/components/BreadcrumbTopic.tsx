import { useEffect } from 'react';
import { GENERIC_MODULES_BASE } from '../services/genericModules';
import { getShellWindow } from '../services/shell';

interface BreadcrumbConfig {
  /** Stable per-extension marker, also the id of the injected loader script. */
  marker: string;
  /** The page's own label, shown last and emphasized. */
  title: string;
  /** The parent node's label, shown before the separator. */
  parent?: string;
  /** Absolute URL of the page icon, a Polarion-served svg. */
  icon?: string;
}

interface BreadcrumbBridge {
  install: (config: BreadcrumbConfig) => void;
}

const LOADER_ID = 'sbb-breadcrumb-bridge-loader';

/**
 * Shows the current page in the Polarion app-header breadcrumb, through the shared generic BreadcrumbBridge
 * injected into the shell window.
 *
 * react-sbb-polarion's own BreadcrumbInjector cannot do this job for a node that has children. The bridge takes
 * a `parent` as well as a `title`, which is what renders "XML-Repair > <icon> Purge outdated data" rather than
 * one flat label, and RSP's component has no such prop. It also returns early when the loader script is already
 * in the shell head, so the label of the first page opened would stick for the rest of the shell session:
 * moving from General checks to Purge outdated data would keep the old label. Calling `install()` on every
 * mount is what re-labels it.
 */
export default function BreadcrumbTopic({ marker, title, parent, icon }: Readonly<BreadcrumbConfig>) {
  useEffect(() => {
    // No accessible shell window (a standalone dev page, or a cross-origin one): the breadcrumb is chrome,
    // never a precondition for the page below it.
    const shell = getShellWindow() as (Window & { SbbBreadcrumbBridge?: BreadcrumbBridge }) | null;
    if (!shell) {
      return;
    }

    try {
      // The optional keys are left out rather than set to undefined, so the bridge sees exactly the keys it is
      // given either way.
      const config: BreadcrumbConfig = { marker: marker, title: title };
      if (parent) {
        config.parent = parent;
      }
      if (icon) {
        config.icon = icon;
      }
      if (shell.SbbBreadcrumbBridge) {
        shell.SbbBreadcrumbBridge.install(config);
        return;
      }

      const shellDocument = shell.document;
      if (!shellDocument?.head) {
        return;
      }
      shellDocument.getElementById(LOADER_ID)?.remove();

      const loader = shellDocument.createElement('script');
      loader.id = LOADER_ID;
      loader.type = 'text/javascript';
      loader.src = `${GENERIC_MODULES_BASE}BreadcrumbBridge.js`;
      loader.dataset.marker = marker;
      loader.dataset.title = title;
      if (parent) {
        loader.dataset.parent = parent;
      }
      if (icon) {
        loader.dataset.icon = icon;
      }
      shellDocument.head.appendChild(loader);
    } catch {
      // A shell that turns unreachable between the lookup and the write changes nothing for this page.
    }
  }, [marker, title, parent, icon]);

  return null;
}
