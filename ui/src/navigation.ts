/**
 * The navigation node ids, and how the extension names itself in the Polarion app header.
 *
 * They live here rather than in `features.tsx` because `pages/Home.tsx` needs the ids while `features.tsx`
 * imports `Home`. Declaring them there makes those two modules circular, and the ids then evaluate too late:
 * the page fails to load with "Cannot access 'GENERAL_CHECKS' before initialization".
 */

/**
 * Each id is both a `?feature=` value of this bundle and a node id on the Java side
 * (`XmlRepairNavigationExtender.HOME_FEATURE`, `.GENERAL_CHECKS` and `.PURGE_OUTDATED_DATA`). A node's
 * `getPageUrl()` puts its own id into the URL it opens, and the Home page appends one to the portal's topic path
 * to select that node in the navigation tree. The two sides therefore have to agree: `test/navigation.test.ts`
 * and `XmlRepairNavigationExtenderTest` pin the same literals from either end, so renaming one alone fails.
 */
export const HOME = 'home';
export const GENERAL_CHECKS = 'general-checks';
export const PURGE_OUTDATED_DATA = 'purge-outdated-data';

/**
 * The breadcrumb title of every page that does not name its own, and the parent label that the two pages below
 * the root node show ahead of their title.
 */
export const EXTENSION_LABEL = 'XML-Repair';

/** The breadcrumb icon of those same pages. Equal to what `XmlRepairNavigationExtender.getIconUrl()` returns. */
export const EXTENSION_ICON = '/polarion/xml-repair-app/ui/images/menu/30x30/_parent.svg';
