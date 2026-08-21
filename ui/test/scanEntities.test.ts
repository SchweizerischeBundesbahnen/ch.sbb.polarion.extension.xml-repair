import { describe, expect, it } from 'vitest';
import {
  applyWriteResults,
  collectIssueGroupCounts,
  collectSelectableKeys,
  collectSelectedIssues,
  entityKey,
  entityKeyToRef,
  hasSubitems,
  issueGroup,
  itemKey,
  pruneSelection,
  subitemKey,
  visibleIssueIndices,
} from '../src/services/scanEntities';
import type { Issue, ScanEntity, ScanResult } from '../src/types';
import { PURGE_SCAN_RESULT, SCAN_RESULT } from './fixtures';

// The pure helpers both result pages share: row keys, the grouping key, and the two result transformations.

const issue = (metaInfo: string, repairer: string, group?: string): Issue => ({
  metaInfo,
  repairer,
  description: metaInfo,
  ...(group ? { group } : {}),
  warnings: [],
});

const entity = (entityId: string, issues: Issue[], extra: Partial<ScanEntity> = {}): ScanEntity => ({
  entityType: 'WORKITEM',
  projectId: 'elibrary',
  space: null,
  entityId,
  revision: null,
  issues,
  fields: {},
  subitems: [],
  warnings: [],
  ...extra,
});

describe('row keys', () => {
  it('identifies a row by project, space and id', () => {
    expect(itemKey(entity('EL-1', []))).toBe('elibrary--EL-1');
    expect(itemKey(entity('DOC-1', [], { space: 'spaceA' }))).toBe('elibrary-spaceA-DOC-1');
  });

  it('nests a subitem key under its parent', () => {
    const sub = entity('DOC-1', [], { space: 'spaceA' });
    expect(subitemKey('parent', sub)).toBe('parent/elibrary-spaceA-DOC-1');
  });

  it('recognizes a row that carries subitems', () => {
    expect(hasSubitems(entity('COLL-1', []))).toBe(false);
    expect(hasSubitems(entity('COLL-1', [], { subitems: [entity('DOC-1', [])] }))).toBe(true);
  });
});

describe('entity dropdown keys', () => {
  it('round-trips a document through its space and name', () => {
    const key = entityKey({ space: 'Specification/Sub', id: 'design', name: 'Design', type: null });
    expect(key).toBe('Specification/Sub/design');
    // Module names cannot contain '/', so the last separator splits a nested space back apart.
    expect(entityKeyToRef(key)).toEqual({ space: 'Specification/Sub', id: 'design' });
  });

  it('treats a key without a separator as a bare id, which is what a collection has', () => {
    expect(entityKey({ space: null, id: 'COLL-1', name: 'Collection', type: null })).toBe('COLL-1');
    expect(entityKeyToRef('COLL-1')).toEqual({ space: null, id: 'COLL-1' });
  });
});

describe('issueGroup', () => {
  it('groups by the repairer unless the issue names a finer group', () => {
    expect(issueGroup(issue('m1', 'FieldsWrongTypeRepairer'))).toBe('FieldsWrongTypeRepairer');
    expect(issueGroup(issue('m2', 'OutdatedCustomFieldsRepairer', 'legacyOwner'))).toBe('legacyOwner');
  });
});

describe('visibleIssueIndices', () => {
  it('leaves out the issues of hidden groups', () => {
    const item = entity('EL-1', [issue('m1', 'A'), issue('m2', 'B'), issue('m3', 'A')]);
    expect(visibleIssueIndices(item, new Set())).toEqual([0, 1, 2]);
    expect(visibleIssueIndices(item, new Set(['A']))).toEqual([1]);
  });
});

describe('collectSelectableKeys', () => {
  it('skips rows that were already repaired, carry a revision, or have nothing visible', () => {
    const items = [
      entity('EL-1', [issue('m1', 'A')]),
      entity('EL-2', [issue('m2', 'A')], { repaired: true }),
      entity('EL-3', [issue('m3', 'A')], { revision: '123' }),
      entity('EL-4', []),
    ];

    expect(collectSelectableKeys(items, new Set()).map((k) => k.key)).toEqual(['elibrary--EL-1']);
    expect(collectSelectableKeys(items, new Set(['A']))).toEqual([]);
  });

  it('offers the documents of a collection rather than the collection row itself', () => {
    const items = [entity('COLL-1', [], { subitems: [entity('DOC-1', [issue('m1', 'A')], { space: 'spaceA' })] })];

    expect(collectSelectableKeys(items, new Set()).map((k) => k.key)).toEqual([
      'elibrary--COLL-1/elibrary-spaceA-DOC-1',
    ]);
  });
});

describe('pruneSelection', () => {
  const result: ScanResult = { report: '', items: [entity('EL-1', [issue('m1', 'A'), issue('m2', 'B')])] };

  it('drops only the indices of the newly hidden group', () => {
    const selected = new Map([['elibrary--EL-1', new Set([0, 1])]]);

    expect(pruneSelection(selected, result, new Set(['A']))).toEqual(new Map([['elibrary--EL-1', new Set([1])]]));
  });

  it('removes a row whose every selected issue is hidden', () => {
    const selected = new Map([['elibrary--EL-1', new Set([0])]]);

    expect(pruneSelection(selected, result, new Set(['A'])).size).toBe(0);
  });

  it('returns the same map when nothing changed, so the table does not re-render', () => {
    const selected = new Map([['elibrary--EL-1', new Set([0, 1])]]);

    expect(pruneSelection(selected, result, new Set())).toBe(selected);
    expect(pruneSelection(selected, null, new Set(['A']))).toBe(selected);
  });
});

describe('collectSelectedIssues', () => {
  it('collects the meta infos of the ticked issues, subitems included', () => {
    const selected = new Map([
      ['elibrary--EL-100', new Set([0, 1])],
      ['elibrary-coll-COLL-1/elibrary-spaceA-DOC-2', new Set([0])],
    ]);

    const { issueMetaInfos, affectedKeys } = collectSelectedIssues(SCAN_RESULT, selected);

    expect(issueMetaInfos.sort()).toEqual(['meta-1', 'meta-2', 'meta-5']);
    expect(affectedKeys.size).toBe(2);
  });

  it('ignores rows with an empty selection and indices that no longer exist', () => {
    const result: ScanResult = { report: '', items: [entity('EL-1', [issue('m1', 'A')])] };
    const selected = new Map([['elibrary--EL-1', new Set([0, 7])]]);

    expect(collectSelectedIssues(result, selected).issueMetaInfos).toEqual(['m1']);
    expect(collectSelectedIssues(result, new Map([['elibrary--EL-1', new Set<number>()]])).issueMetaInfos).toEqual([]);
  });
});

describe('applyWriteResults', () => {
  it('annotates the affected rows and flags the fully written ones', () => {
    const result: ScanResult = { report: '', items: [entity('EL-1', [issue('m1', 'A'), issue('m2', 'A')])] };
    const written = applyWriteResults(result, new Set(['elibrary--EL-1']), [
      { issueMetaInfo: 'm1', success: true, warnings: [] },
      { issueMetaInfo: 'm2', success: true, warnings: ['took a shortcut'] },
    ]);

    expect(written.items[0].repaired).toBe(true);
    expect(written.items[0].issues[1].repairResult).toEqual({ success: true, warnings: ['took a shortcut'] });
  });

  it('leaves a row unflagged while any of its issues failed or was not written', () => {
    const result: ScanResult = { report: '', items: [entity('EL-1', [issue('m1', 'A'), issue('m2', 'A')])] };
    const written = applyWriteResults(result, new Set(['elibrary--EL-1']), [
      { issueMetaInfo: 'm1', success: false, warnings: [] },
    ]);

    expect(written.items[0].repaired).toBe(false);
    expect(written.items[0].issues[1].repairResult).toBeUndefined();
  });

  it('keeps untouched rows and untouched collections identical', () => {
    const written = applyWriteResults(SCAN_RESULT, new Set(['elibrary--EL-100']), [
      { issueMetaInfo: 'meta-1', success: true, warnings: [] },
    ]);

    expect(written.items[1]).toBe(SCAN_RESULT.items[1]);
    expect(written.items[2]).toBe(SCAN_RESULT.items[2]);
  });

  it('annotates a document inside a collection', () => {
    const key = 'elibrary-coll-COLL-1/elibrary-spaceA-DOC-1';
    const written = applyWriteResults(SCAN_RESULT, new Set([key]), [
      { issueMetaInfo: 'meta-4', success: true, warnings: [] },
    ]);

    expect(written.items[2].subitems[0].repaired).toBe(true);
    // The sibling document of the same collection is not touched.
    expect(written.items[2].subitems[1]).toBe(SCAN_RESULT.items[2].subitems[1]);
  });
});

describe('collectIssueGroupCounts', () => {
  it('counts each attribute across the whole result, subitems included', () => {
    const counts = collectIssueGroupCounts(PURGE_SCAN_RESULT);

    expect(Object.fromEntries(counts)).toEqual({ legacyOwner: 2, oldEstimate: 1, obsoleteFlag: 1 });
  });

  it('falls back to the repairer when issues carry no group', () => {
    const counts = collectIssueGroupCounts(SCAN_RESULT);

    expect(counts.get('FieldsInvalidEnumerationValueRepairer')).toBe(3);
  });
});
