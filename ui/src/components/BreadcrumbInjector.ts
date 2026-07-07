import { useEffect } from 'react';

export default function BreadcrumbInjector() {
  useEffect(() => {
    try {
      const parentDocument = window.parent.document;
      if (!parentDocument?.head) return;

      if (parentDocument.getElementById('xml-repair-breadcrumb-bridge')) return;

      // Inject the shared generic BreadcrumbBridge into the parent window, configured for this app
      // via data-* attributes. (Replaces the former per-extension xml-repair-breadcrumb-bridge.js.)
      const scriptElement = parentDocument.createElement('script');
      scriptElement.id = 'xml-repair-breadcrumb-bridge';
      scriptElement.type = 'text/javascript';
      // Absolute (the parent fetches it) but with no hardcoded /<ext>-app/: derived from our own path.
      scriptElement.src = window.location.pathname.replace(/\/ui\/.*$/, '/ui/generic/js/modules/') + 'BreadcrumbBridge.js';
      scriptElement.dataset.marker = 'xml-repair';
      scriptElement.dataset.title = 'XML-Repair';
      scriptElement.dataset.icon = '/polarion/xml-repair-admin/ui/images/menu/30x30/_parent.svg';
      parentDocument.head.appendChild(scriptElement);
    } catch {
      // Cross-origin parent or access denied — skip breadcrumb injection
    }
  }, []);

  return null;
}
