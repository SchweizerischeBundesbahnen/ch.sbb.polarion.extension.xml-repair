import { PageLayout } from '@sbb-polarion/react-sbb-polarion';
import { GENERAL_CHECKS, PURGE_OUTDATED_DATA } from '../navigation';
import { getShellWindow } from '../services/shell';

/** The topic path of a sub-topic: Polarion's own topic URL with the node id appended. */
export function subTopicHref(currentHref: string, nodeId: string): string {
  return `${currentHref.replace(/\/+$/, '')}/${nodeId}`;
}

/** The same page inside this bundle, for when there is no portal shell to navigate. */
export function localHref(nodeId: string): string {
  const params = new URLSearchParams(window.location.search);
  params.set('feature', nodeId);
  return `?${params.toString()}`;
}

const PAGES = [
  { id: GENERAL_CHECKS, label: 'General checks', description: 'Scan entities for XML issues and repair them.' },
  {
    id: PURGE_OUTDATED_DATA,
    label: 'Purge outdated data',
    description: 'Clear attributes which are filled but no longer defined as custom fields.',
  },
];

/**
 * The entry page of the XML-Repair navigation node: one link per page below it.
 *
 * In Polarion this page sits in an iframe of the portal shell, whose URL is the node's own topic path. Appending
 * the sub-node id to that URL is what makes the portal select the sub-node in the navigation tree, which then
 * loads the page from that node's `getPageUrl()`. Standing alone - `vite dev`, a test - there is no shell, so
 * the feature router is addressed directly instead.
 */
export default function Home() {
  const openPage = (nodeId: string) => {
    const shell = getShellWindow();
    if (shell) {
      shell.location.assign(subTopicHref(shell.location.href, nodeId));
      return;
    }
    window.location.assign(localHref(nodeId));
  };

  return (
    <PageLayout>
      <div className="xml-repair-app">
        <div className="home-page">
          <h3>XML-Repair</h3>
          <p>Please select below what you wish to do:</p>
          <ul className="home-page-links">
            {PAGES.map((page) => (
              <li key={page.id}>
                <button type="button" className="link-button" onClick={() => openPage(page.id)}>
                  {page.label}
                </button>
                <span className="home-page-desc">{page.description}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </PageLayout>
  );
}
