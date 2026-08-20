import { useCallback, useMemo, useState } from 'react';
import type { ScanEntity, ScanResult } from '../types';
import { collectSelectableKeys, itemKey, pruneSelection, subitemKey, visibleIssueIndices } from './scanEntities';

interface ScanSelectionOptions {
  /** The displayed scan result, or null before the first scan. */
  result: ScanResult | null;
  /** Issue groups the user filtered out; their issues can neither be shown nor selected. */
  hiddenGroups: Set<string>;
  /** True while a write is in flight, which freezes the table. */
  busy: boolean;
}

/**
 * The row and issue selection of a results table, shared by the Scan & Repair and the Purge pages: both let the
 * user tick whole entities, individual issues, or a collection's documents at once, then hand the ticked issues
 * to the same backend call.
 *
 * Selections are keyed by row (see `itemKey` / `subitemKey`) and hold issue indices, so an entity that appears
 * twice at different revisions keeps its own ticks.
 */
export default function useScanSelection({ result, hiddenGroups, busy }: ScanSelectionOptions) {
  const [selectedIssues, setSelectedIssues] = useState<Map<string, Set<number>>>(new Map());
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  const reset = useCallback(() => {
    setSelectedIssues(new Map());
    setExpandedRows(new Set());
  }, []);

  /** Ticks every visible issue of a row, or clears the row when they are all ticked already. */
  const toggleEntitySelection = useCallback((key: string, indices: number[]) => {
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      const current = next.get(key) || new Set();
      const allSelected = indices.length > 0 && indices.every((i) => current.has(i));
      if (allSelected) {
        next.delete(key);
      } else {
        next.set(key, new Set(indices));
      }
      return next;
    });
  }, []);

  const toggleIssueSelection = useCallback((key: string, issueIndex: number) => {
    setSelectedIssues((prev) => {
      const next = new Map(prev);
      const current = new Set(next.get(key) || []);
      if (current.has(issueIndex)) {
        current.delete(issueIndex);
      } else {
        current.add(issueIndex);
      }
      if (current.size === 0) {
        next.delete(key);
      } else {
        next.set(key, current);
      }
      return next;
    });
  }, []);

  /** A collection row ticks or clears all of its selectable documents at once. */
  const toggleCollectionSelection = useCallback(
    (item: ScanEntity) => {
      const parentKey = itemKey(item);
      const subs = item.subitems
        .filter((sub) => sub.issues.length > 0 && !sub.repaired && !sub.revision)
        .map((sub) => ({ sub, indices: visibleIssueIndices(sub, hiddenGroups) }))
        .filter(({ indices }) => indices.length > 0);
      setSelectedIssues((prev) => {
        const allSubsSelected =
          subs.length > 0 &&
          subs.every(({ sub, indices }) => {
            const selected = prev.get(subitemKey(parentKey, sub));
            return !!selected && indices.every((i) => selected.has(i));
          });
        const next = new Map(prev);
        if (allSubsSelected) {
          subs.forEach(({ sub }) => next.delete(subitemKey(parentKey, sub)));
        } else {
          subs.forEach(({ sub, indices }) => {
            next.set(subitemKey(parentKey, sub), new Set(indices));
          });
        }
        return next;
      });
    },
    [hiddenGroups],
  );

  const toggleExpanded = useCallback(
    (key: string) => {
      if (busy) return;
      setExpandedRows((prev) => {
        const next = new Set(prev);
        if (next.has(key)) next.delete(key);
        else next.add(key);
        return next;
      });
    },
    [busy],
  );

  const selectableKeys = useMemo(
    () => (result ? collectSelectableKeys(result.items, hiddenGroups) : []),
    [result, hiddenGroups],
  );

  const allItemsSelected =
    selectableKeys.length > 0 &&
    selectableKeys.every(({ key, indices }) => {
      const selected = selectedIssues.get(key);
      return !!selected && indices.every((i) => selected.has(i));
    });
  const hasAnySelection = selectedIssues.size > 0;
  const someItemsSelected = hasAnySelection && !allItemsSelected;
  const selectedIssueCount = [...selectedIssues.values()].reduce((sum, indices) => sum + indices.size, 0);

  const toggleSelectAll = useCallback(() => {
    if (allItemsSelected) {
      setSelectedIssues(new Map());
    } else {
      const next = new Map<string, Set<number>>();
      selectableKeys.forEach(({ key, indices }) => {
        next.set(key, new Set(indices));
      });
      setSelectedIssues(next);
    }
  }, [allItemsSelected, selectableKeys]);

  /** Call after changing the group filter, so selections of newly hidden issues do not survive unseen. */
  const pruneHiddenGroups = useCallback(
    (nextHiddenGroups: Set<string>) => {
      setSelectedIssues((prev) => pruneSelection(prev, result, nextHiddenGroups));
    },
    [result],
  );

  /** Expands every row that currently shows issues, and its visible subitems. */
  const expandAll = useCallback((keys: string[]) => setExpandedRows(new Set(keys)), []);
  const collapseAll = useCallback(() => setExpandedRows(new Set()), []);

  return {
    selectedIssues,
    expandedRows,
    hasAnySelection,
    selectedIssueCount,
    allItemsSelected,
    someItemsSelected,
    reset,
    clearSelection: useCallback(() => setSelectedIssues(new Map()), []),
    toggleEntitySelection,
    toggleIssueSelection,
    toggleCollectionSelection,
    toggleExpanded,
    toggleSelectAll,
    pruneHiddenGroups,
    expandAll,
    collapseAll,
  };
}
