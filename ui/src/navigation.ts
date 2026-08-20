/**
 * The navigation node ids and the extension's own labelling, kept out of `features.tsx` so that a page can name
 * a sibling node without importing the registry that lists the pages themselves.
 *
 * Each id is both a `?feature=` value and the `NODE_ID` of the matching Java class in
 * `ch.sbb.polarion.extension.xml_repair`: the node's `getPageUrl()` puts its own id into the URL, and the Home
 * page appends it to the portal's topic path. The two sides must not drift; a test pins them.
 */
export const HOME = 'home';
export const GENERAL_CHECKS = 'general-checks';
export const PURGE_OUTDATED_DATA = 'purge-outdated-data';

/** The extension's own label and icon, used wherever a page does not name its own breadcrumb. */
export const EXTENSION_LABEL = 'XML-Repair';
export const EXTENSION_ICON = '/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg';
