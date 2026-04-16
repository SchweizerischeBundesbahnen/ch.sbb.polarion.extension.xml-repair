import { useEffect } from 'react';

export default function BreadcrumbInjector() {
  useEffect(() => {
    try {
      const parentDocument = window.parent.document;
      if (!parentDocument?.head) return;

      if (parentDocument.getElementById('xml-repair-breadcrumb-bridge')) return;

      const scriptElement = parentDocument.createElement('script');
      scriptElement.id = 'xml-repair-breadcrumb-bridge';
      scriptElement.type = 'text/javascript';
      scriptElement.src = '/polarion/xml-repair/ui/js/xml-repair-breadcrumb-bridge.js';
      parentDocument.head.appendChild(scriptElement);
    } catch {
      // Cross-origin parent or access denied — skip breadcrumb injection
    }
  }, []);

  return null;
}
