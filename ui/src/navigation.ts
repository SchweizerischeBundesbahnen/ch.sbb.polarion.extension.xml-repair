/**
 * The navigation node ids and the extension's own labelling, kept out of `features.tsx` so that a page can name
 * a sibling node without importing the registry that lists the pages themselves.
 *
 * Each id is both a `?feature=` value and a node id declared in `XmlRepairNavigationExtender`
 * (`HOME_FEATURE`, `GENERAL_CHECKS`, `PURGE_OUTDATED_DATA`): the node's `getPageUrl()` puts its own id into the
 */
export const HOME = 'home';
export const GENERAL_CHECKS = 'general-checks';
export const PURGE_OUTDATED_DATA = 'purge-outdated-data';

/** The extension's own label and icon, used wherever a page does not name its own breadcrumb. */
export const EXTENSION_LABEL = 'XML-Repair';
export const EXTENSION_ICON = '/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg';
