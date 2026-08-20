import type { EntityInfo, EntityRef, Issue, RepairIssueResult, RepairResult, ScanEntity, ScanResult } from '../types';

/**
 * Pure helpers over a scan result, shared by the Scan & Repair and the Purge pages. They own the row keys, the
 * grouping key and the two result transformations, so both pages address the same entity by the same string and
 * cannot drift apart.
 */

/** A collection row carries its documents as subitems; every other entity type has none. */
export const hasSubitems = (item: ScanEntity): boolean => item.subitems && item.subitems.length > 0;

export const itemKey = (item: ScanEntity): string => `${item.projectId}-${item.space || ''}-${item.entityId}`;

export const subitemKey = (parentKey: string, sub: ScanEntity): string =>
  `${parentKey}/${sub.projectId}-${sub.space || ''}-${sub.entityId}`;

/**
 * What issues are grouped, filtered and counted by. The repairer is the default grouping; a repairer that
 * reports several distinct kinds of issue sets `group` to split them, which is what the Purge page uses to
 * group per attribute.
 */
export const issueGroup = (issue: Issue): string => issue.group ?? issue.repairer;

/**
 * One selected entity as a single dropdown option value. A document is identified by space + module name, a
 * collection by its id alone; module names cannot contain '/', so the last separator splits the key back apart
 * even for a nested space like "Specification/Sub".
 */
export const entityKey = (entity: EntityInfo): string => (entity.space ? `${entity.space}/${entity.id}` : entity.id);

export const entityKeyToRef = (key: string): EntityRef => {
  const separator = key.lastIndexOf('/');
  return separator < 0 ? { space: null, id: key } : { space: key.slice(0, separator), id: key.slice(separator + 1) };
};

/** The indices of an entity's issues that the current group filter leaves visible. */
export const visibleIssueIndices = (entity: ScanEntity, hiddenGroups: Set<string>): number[] => {
  const out: number[] = [];
  entity.issues.forEach((issue, i) => {
    if (!hiddenGroups.has(issueGroup(issue))) out.push(i);
  });
  return out;
};

/**
 * Every row the user can tick, with the issue indices that ticking it selects. A row is skipped when it has no
 * issues, when it was already repaired, or when it comes from a revision - the backend refuses to write those.
 */
export const collectSelectableKeys = (
  items: ScanEntity[],
  hiddenGroups: Set<string>,
): { key: string; indices: number[] }[] => {
  const keys: { key: string; indices: number[] }[] = [];
  for (const item of items) {
    if (hasSubitems(item)) {
      const parentKey = itemKey(item);
      for (const sub of item.subitems) {
        if (sub.issues.length > 0 && !sub.repaired && !sub.revision) {
          const indices = visibleIssueIndices(sub, hiddenGroups);
          if (indices.length > 0) keys.push({ key: subitemKey(parentKey, sub), indices });
        }
      }
    } else if (item.issues.length > 0 && !item.repaired && !item.revision) {
      const indices = visibleIssueIndices(item, hiddenGroups);
      if (indices.length > 0) keys.push({ key: itemKey(item), indices });
    }
  }
  return keys;
};

/**
 * Drops selections the group filter just hid. Returns the same map instance when nothing changed, so hiding a
 * group that nobody selected does not re-render the results.
 */
export const pruneSelection = (
  selected: Map<string, Set<number>>,
  result: ScanResult | null,
  hiddenGroups: Set<string>,
): Map<string, Set<number>> => {
  if (!result) return selected;

  const hiddenIndicesByKey = new Map<string, Set<number>>();
  const collect = (entity: ScanEntity, key: string) => {
    const hiddenIdx = new Set<number>();
    entity.issues.forEach((issue, i) => {
      if (hiddenGroups.has(issueGroup(issue))) hiddenIdx.add(i);
    });
    if (hiddenIdx.size > 0) hiddenIndicesByKey.set(key, hiddenIdx);
  };
  for (const item of result.items) {
    if (hasSubitems(item)) {
      const parentKey = itemKey(item);
      for (const sub of item.subitems) {
        if (sub.issues.length > 0) collect(sub, subitemKey(parentKey, sub));
      }
    } else if (item.issues.length > 0) {
      collect(item, itemKey(item));
    }
  }

  const updated = new Map(selected);
  let changed = false;
  for (const [key, indices] of selected) {
    const hiddenIdx = hiddenIndicesByKey.get(key);
    if (!hiddenIdx) continue;
    const filtered = new Set<number>();
    indices.forEach((i) => {
      if (!hiddenIdx.has(i)) filtered.add(i);
    });
    if (filtered.size !== indices.size) {
      changed = true;
      if (filtered.size === 0) updated.delete(key);
      else updated.set(key, filtered);
    }
  }
  return changed ? updated : selected;
};

/**
 * The meta infos of every ticked issue, plus the row keys they came from. The meta info is opaque: the backend
 * decodes it back into an entity plus a repairer, and matches its results to these exact strings.
 */
export const collectSelectedIssues = (
  result: ScanResult,
  selectedIssues: Map<string, Set<number>>,
): { issueMetaInfos: string[]; affectedKeys: Set<string> } => {
  const issueMetaInfos: string[] = [];
  const affectedKeys = new Set<string>();

  const collect = (entity: ScanEntity, key: string) => {
    const selected = selectedIssues.get(key);
    if (!selected || selected.size === 0) return;
    selected.forEach((i) => {
      if (entity.issues[i]) issueMetaInfos.push(entity.issues[i].metaInfo);
    });
    affectedKeys.add(key);
  };

  for (const item of result.items) {
    if (hasSubitems(item)) {
      const parentKey = itemKey(item);
      for (const sub of item.subitems) {
        collect(sub, subitemKey(parentKey, sub));
      }
    } else {
      collect(item, itemKey(item));
    }
  }
  return { issueMetaInfos, affectedKeys };
};

/** Annotates the affected rows with what the backend reported per issue, and flags fully repaired ones. */
export const applyWriteResults = (
  result: ScanResult,
  affectedKeys: Set<string>,
  writeResults: RepairResult[],
): ScanResult => {
  const resultByMetaInfo = new Map<string, RepairIssueResult>();
  for (const r of writeResults) {
    resultByMetaInfo.set(r.issueMetaInfo, { success: r.success, warnings: [...(r.warnings || [])] });
  }

  const annotateIssues = (issues: Issue[]): Issue[] =>
    issues.map((issue) => {
      const writeResult = resultByMetaInfo.get(issue.metaInfo);
      return writeResult ? { ...issue, repairResult: writeResult } : issue;
    });
  const allRepaired = (issues: Issue[]): boolean => issues.every((issue) => issue.repairResult?.success);

  return {
    ...result,
    items: result.items.map((item) => {
      const key = itemKey(item);
      if (hasSubitems(item)) {
        if (!item.subitems.some((sub) => affectedKeys.has(subitemKey(key, sub)))) return item;
        return {
          ...item,
          subitems: item.subitems.map((sub) => {
            const subKey = subitemKey(key, sub);
            if (!affectedKeys.has(subKey)) return sub;
            const updated = annotateIssues(sub.issues);
            return { ...sub, issues: updated, repaired: allRepaired(updated) };
          }),
        };
      }
      if (!affectedKeys.has(key)) return item;
      const updated = annotateIssues(item.issues);
      return { ...item, issues: updated, repaired: allRepaired(updated) };
    }),
  };
};

/**
 * How many issues each group accounts for across the whole result, subitems included. The Purge page turns this
 * into its attribute list: every key is an attribute that some scanned entity still has filled.
 */
export const collectIssueGroupCounts = (result: ScanResult): Map<string, number> => {
  const counts = new Map<string, number>();
  const walk = (items: ScanEntity[]): void => {
    items.forEach((item) => {
      item.issues.forEach((issue) => {
        const group = issueGroup(issue);
        counts.set(group, (counts.get(group) ?? 0) + 1);
      });
      if (hasSubitems(item)) walk(item.subitems);
    });
  };
  walk(result.items);
  return counts;
};
