import { describe, expect, it } from 'vitest';
import { FEATURES, findFeature } from '../src/features';
import { EXTENSION_LABEL, GENERAL_CHECKS, HOME, PURGE_OUTDATED_DATA } from '../src/navigation';

// The node ids are a contract with the Java side: XmlRepairNavigationExtender puts these exact strings into
// `?feature=` - HOME_FEATURE for the root node, GENERAL_CHECKS and PURGE_OUTDATED_DATA for the two
// XmlRepairNavigationNodes below it - and Home appends them to the portal's topic path. The Java tests pin the
// same literals from the other side, so changing one without the other fails here or there.

describe('navigation node ids', () => {
  it('matches the ids the Java navigation nodes emit', () => {
    expect(HOME).toBe('home');
    expect(GENERAL_CHECKS).toBe('general-checks');
    expect(PURGE_OUTDATED_DATA).toBe('purge-outdated-data');
  });

  it('has a feature registered for every navigation node', () => {
    for (const id of [HOME, GENERAL_CHECKS, PURGE_OUTDATED_DATA]) {
      expect(findFeature(id), `no feature for node ${id}`).toBeDefined();
    }
  });
});

describe('findFeature', () => {
  it('resolves the admin features hivemodule.xml points at', () => {
    expect(findFeature('about')?.label).toBe('About');
    expect(findFeature('authorization')?.label).toBe('Repair Authorization');
  });

  it('keeps the old ?feature=repair bookmarks working', () => {
    expect(findFeature('repair')?.id).toBe(GENERAL_CHECKS);
  });

  it('returns nothing for an unknown or missing feature, which falls back to the dev Landing', () => {
    expect(findFeature('nope')).toBeUndefined();
    expect(findFeature(null)).toBeUndefined();
  });

  it('names the two navigation pages under the extension in the breadcrumb', () => {
    expect(findFeature(GENERAL_CHECKS)?.breadcrumbTitle).toBe('General checks');
    expect(findFeature(GENERAL_CHECKS)?.breadcrumbParent).toBe(EXTENSION_LABEL);
    expect(findFeature(PURGE_OUTDATED_DATA)?.breadcrumbParent).toBe(EXTENSION_LABEL);
    // The admin pages keep the extension's own label, so they name no breadcrumb of their own.
    expect(findFeature('about')?.breadcrumbTitle).toBeUndefined();
  });

  it('lists every feature exactly once', () => {
    const ids = FEATURES.map((f) => f.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
