import React, { memo, useState } from 'react';
import type { Repairer, ScanEntity, ScanResult } from '../types';
import IssueList from './IssueList';
import RepairerBreakdownTable from './RepairerBreakdownTable';

// Memoized to prevent DOM re-creation on parent re-renders (dangerouslySetInnerHTML causes blink otherwise)
const EntityRef = memo(({ html, fallback }: { html?: string; fallback: string }) =>
  html ? <span className="entity-ref" dangerouslySetInnerHTML={{ __html: html }} /> : <>{fallback}</>,
);

const hasSubitems = (item: ScanEntity): boolean => item.subitems && item.subitems.length > 0;
const itemKey = (item: ScanEntity): string => `${item.projectId}-${item.space || ''}-${item.entityId}`;
const subitemKey = (parentKey: string, sub: ScanEntity): string =>
  `${parentKey}/${sub.projectId}-${sub.space || ''}-${sub.entityId}`;
const REVISIONED_TOOLTIP = 'Items at a specific revision cannot be repaired';

interface ResultsTableProps {
  result: ScanResult;
  hideValidAtScanTime: boolean;
  hiddenRepairers: Set<string>;
  onToggleRepairer: (id: string) => void;
  repairers: Repairer[];
  selectedIssues: Map<string, Set<number>>;
  expandedRows: Set<string>;
  repairingEntity: string | null;
  batchRepairing: boolean;
  onToggleEntitySelection: (entityKey: string, indices: number[]) => void;
  onToggleCollectionSelection: (item: ScanEntity) => void;
  onToggleIssueSelection: (entityKey: string, issueIndex: number) => void;
  onToggleExpanded: (entityKey: string) => void;
  onToggleSelectAll: () => void;
  onExpandAll: (keys: string[]) => void;
  onCollapseAll: () => void;
  allItemsSelected: boolean;
  someItemsSelected: boolean;
}

export default function ResultsTable({
  result,
  hideValidAtScanTime,
  hiddenRepairers,
  onToggleRepairer,
  repairers,
  selectedIssues,
  expandedRows,
  repairingEntity,
  batchRepairing,
  onToggleEntitySelection,
  onToggleCollectionSelection,
  onToggleIssueSelection,
  onToggleExpanded,
  onToggleSelectAll,
  onExpandAll,
  onCollapseAll,
  allItemsSelected,
  someItemsSelected,
}: ResultsTableProps) {
  const visibleIssueCount = (entity: ScanEntity): number =>
    entity.issues.filter((iss) => !hiddenRepairers.has(iss.repairer)).length;
  const visibleIssueIndices = (entity: ScanEntity): number[] => {
    const out: number[] = [];
    entity.issues.forEach((iss, i) => {
      if (!hiddenRepairers.has(iss.repairer)) out.push(i);
    });
    return out;
  };
  const visibleIssuesInItem = (item: ScanEntity): number =>
    hasSubitems(item) ? item.subitems.reduce((sum, sub) => sum + visibleIssueCount(sub), 0) : visibleIssueCount(item);
  const subitemIsVisible = (sub: ScanEntity): boolean => {
    if (!hideValidAtScanTime) return true;
    return sub.issues.length === 0 || visibleIssueCount(sub) > 0;
  };
  const itemIsVisible = (item: ScanEntity): boolean => {
    if (!hideValidAtScanTime) return true;
    if (hasSubitems(item)) {
      const originalTotal = item.subitems.reduce((s, sub) => s + sub.issues.length, 0);
      return originalTotal === 0 || item.subitems.some((sub) => visibleIssueCount(sub) > 0);
    }
    return item.issues.length === 0 || visibleIssueCount(item) > 0;
  };

  const itemsWithIssues = result.items.filter((item) => {
    if (hasSubitems(item)) return item.subitems.some((sub) => visibleIssueCount(sub) > 0);
    return visibleIssueCount(item) > 0;
  });

  const issuesByRepairer = new Map<string, number>();
  const collectIssues = (items: ScanEntity[]): void => {
    items.forEach((item) => {
      item.issues.forEach((issue) => {
        issuesByRepairer.set(issue.repairer, (issuesByRepairer.get(issue.repairer) ?? 0) + 1);
      });
      if (hasSubitems(item)) collectIssues(item.subitems);
    });
  };
  collectIssues(result.items);
  const repairerBreakdown = Array.from(issuesByRepairer.entries())
    .map(([id, count]) => ({ id, name: repairers.find((r) => r.id === id)?.name || id, count }))
    .sort((a, b) => b.count - a.count);
  const totalIssues = repairerBreakdown.reduce((sum, b) => sum + b.count, 0);

  const [breakdownOpen, setBreakdownOpen] = useState(false);

  const summaryText = (
    <>
      {totalIssues} {totalIssues === 1 ? 'issue' : 'issues'} in {result.items.length}{' '}
      {result.items.length === 1 ? 'item' : 'items'}
    </>
  );

  return (
    <div className="results-section">
      <h3>
        Results
        {result.items.length > 0 && (
          <>
            {' '}
            (
            {totalIssues > 0 ? (
              <button
                type="button"
                className="breakdown-toggle"
                onClick={() => setBreakdownOpen((open) => !open)}
              >
                {summaryText}
              </button>
            ) : (
              summaryText
            )}
            )
          </>
        )}
      </h3>
      {totalIssues > 0 && breakdownOpen && (
        <RepairerBreakdownTable
          rows={repairerBreakdown}
          hiddenRepairers={hiddenRepairers}
          onToggleRepairer={onToggleRepairer}
        />
      )}

      {result.items.length > 0 ? (
        <table className={`issues-table${batchRepairing ? ' disabled' : ''}`}>
          <thead>
            <tr>
              <th className="col-checkbox">
                {itemsWithIssues.length > 0 && (
                  <input
                    type="checkbox"
                    ref={(el) => {
                      if (el) el.indeterminate = someItemsSelected;
                    }}
                    checked={allItemsSelected}
                    onChange={onToggleSelectAll}
                    disabled={batchRepairing}
                  />
                )}
              </th>
              <th className="col-issues">Issues</th>
              <th style={{ position: 'relative' }}>
                Entity
                {itemsWithIssues.length > 0 &&
                  (() => {
                    const allExpandableKeys: string[] = [];
                    itemsWithIssues.forEach((it) => {
                      const parentKey = itemKey(it);
                      allExpandableKeys.push(parentKey);
                      if (hasSubitems(it)) {
                        it.subitems.forEach((sub) => {
                          if (visibleIssueCount(sub) > 0) {
                            allExpandableKeys.push(subitemKey(parentKey, sub));
                          }
                        });
                      }
                    });
                    const allExpanded = allExpandableKeys.every((k) => expandedRows.has(k));
                    const noneExpanded = expandedRows.size === 0;
                    return (
                      <span className="expand-all-controls">
                        <span
                          className={`expand-all-btn${allExpanded ? ' disabled' : ''}`}
                          title="Expand all"
                          onClick={() => {
                            if (!allExpanded) onExpandAll(allExpandableKeys);
                          }}
                        >
                          &#9662;
                        </span>
                        <span
                          className={`expand-all-btn${noneExpanded ? ' disabled' : ''}`}
                          title="Collapse all"
                          onClick={() => {
                            if (!noneExpanded) onCollapseAll();
                          }}
                        >
                          &#9652;
                        </span>
                      </span>
                    );
                  })()}
              </th>
            </tr>
          </thead>
          <tbody>
            {result.items.filter(itemIsVisible).map((item) => {
              const entityKey = itemKey(item);
              const isCollection = hasSubitems(item);
              const issueCount = isCollection ? visibleIssuesInItem(item) : visibleIssueCount(item);
              const hasIssues = issueCount > 0;
              const isExpanded = expandedRows.has(entityKey);
              const isRepairing = repairingEntity === entityKey;

              let allSelected;
              let someSelected = false;
              let selectableInCollection = false;
              if (isCollection) {
                const subs = item.subitems
                  .filter((sub) => sub.issues.length > 0 && !sub.repaired && !sub.revision)
                  .map((sub) => ({ sub, indices: visibleIssueIndices(sub) }))
                  .filter(({ indices }) => indices.length > 0);
                selectableInCollection = subs.length > 0;
                const allSubsSelected =
                  subs.length > 0 &&
                  subs.every(({ sub, indices }) => {
                    const sel = selectedIssues.get(subitemKey(entityKey, sub));
                    return !!sel && indices.every((i) => sel.has(i));
                  });
                const anySubSelected = subs.some(
                  ({ sub }) => (selectedIssues.get(subitemKey(entityKey, sub))?.size || 0) > 0,
                );
                allSelected = allSubsSelected;
                someSelected = anySubSelected && !allSubsSelected;
              } else {
                const selected = selectedIssues.get(entityKey) || new Set();
                const visibleIdx = visibleIssueIndices(item);
                allSelected = visibleIdx.length > 0 && visibleIdx.every((i) => selected.has(i));
                someSelected = selected.size > 0 && !allSelected;
              }
              const itemRevisioned = !isCollection && !!item.revision;
              const checkboxDisabled =
                batchRepairing || !hasIssues || itemRevisioned || (isCollection && !selectableInCollection);

              return (
                <React.Fragment key={entityKey}>
                  <tr className={`${item.repaired ? 'row-fixed' : ''}${isRepairing ? ' row-repairing' : ''}`}>
                    <td className="col-checkbox">
                      {!item.repaired && (
                        <input
                          type="checkbox"
                          ref={(el) => {
                            if (el) el.indeterminate = someSelected;
                          }}
                          checked={allSelected}
                          onChange={() =>
                            isCollection
                              ? onToggleCollectionSelection(item)
                              : onToggleEntitySelection(entityKey, visibleIssueIndices(item))
                          }
                          disabled={checkboxDisabled}
                          title={itemRevisioned ? REVISIONED_TOOLTIP : undefined}
                        />
                      )}
                      {item.repaired && <span className="fixed-badge">&#10003;</span>}
                    </td>
                    <td
                      className={`col-issues${hasIssues ? ' clickable' : ''}`}
                      style={issueCount > 0 ? { color: '#000', fontWeight: 'bold' } : { color: '#ccc' }}
                      title={hasIssues ? (isExpanded ? 'Hide details' : 'Click to see details') : ''}
                      onClick={() => hasIssues && onToggleExpanded(entityKey)}
                    >
                      {issueCount}
                      {item.warnings && item.warnings.length > 0 && (
                        <span className="warning-icon">
                          &#9888;
                          <span className="warning-popup">
                            {item.warnings.map((w, i) => (
                              <span key={i} className="warning-popup-item">
                                {w}
                              </span>
                            ))}
                          </span>
                        </span>
                      )}
                    </td>
                    <td className="entity-cell">
                      {isRepairing && <img className="spinner spinner-sm" src="/polarion/ria/images/progressWheel48.svg" alt="" />}
                      <EntityRef html={item.fields?.['$_self']?.renderedValue} fallback={item.entityId} />
                      <span
                        className={`expand-arrow${hasIssues ? ' clickable' : ''}`}
                        title={hasIssues ? (isExpanded ? 'Collapse' : 'Expand') : ''}
                        onClick={() => hasIssues && onToggleExpanded(entityKey)}
                      >
                        {isExpanded ? '\u25B4' : '\u25BE'}
                      </span>
                    </td>
                  </tr>

                  {isExpanded && !isCollection && hasIssues && (
                    <tr className="expand-row">
                      <td colSpan={3}>
                        <IssueList
                          issues={item.issues}
                          selected={selectedIssues.get(entityKey) || new Set()}
                          repairers={repairers}
                          hiddenRepairers={hiddenRepairers}
                          onToggle={(i) => onToggleIssueSelection(entityKey, i)}
                          disabled={batchRepairing || itemRevisioned}
                          disabledTitle={itemRevisioned ? REVISIONED_TOOLTIP : undefined}
                        />
                      </td>
                    </tr>
                  )}

                  {isExpanded &&
                    isCollection &&
                    item.subitems.filter(subitemIsVisible).map((sub) => {
                      const subKey = subitemKey(entityKey, sub);
                      const subVisibleCount = visibleIssueCount(sub);
                      const subHasIssues = subVisibleCount > 0;
                      const subVisibleIdx = visibleIssueIndices(sub);
                      const subSelected = selectedIssues.get(subKey) || new Set();
                      const subAllSelected = subVisibleIdx.length > 0 && subVisibleIdx.every((i) => subSelected.has(i));
                      const subSomeSelected = subSelected.size > 0 && !subAllSelected;
                      const subIsExpanded = expandedRows.has(subKey);
                      const subIsRepairing = repairingEntity === subKey;

                      return (
                        <React.Fragment key={subKey}>
                          <tr
                            className={`subitem-row${sub.repaired ? ' row-fixed' : ''}${subIsRepairing ? ' row-repairing' : ''}`}
                          >
                            <td className="col-checkbox">
                              {!sub.repaired && (
                                <input
                                  type="checkbox"
                                  ref={(el) => {
                                    if (el) el.indeterminate = subSomeSelected;
                                  }}
                                  checked={subAllSelected}
                                  onChange={() => onToggleEntitySelection(subKey, subVisibleIdx)}
                                  disabled={batchRepairing || !subHasIssues || !!sub.revision}
                                  title={sub.revision ? REVISIONED_TOOLTIP : undefined}
                                />
                              )}
                              {sub.repaired && <span className="fixed-badge">&#10003;</span>}
                            </td>
                            <td
                              className={`col-issues${subHasIssues ? ' clickable' : ''}`}
                              style={subVisibleCount > 0 ? { color: '#000', fontWeight: 'bold' } : { color: '#ccc' }}
                              title={subHasIssues ? (subIsExpanded ? 'Hide details' : 'Click to see details') : ''}
                              onClick={() => subHasIssues && onToggleExpanded(subKey)}
                            >
                              {subVisibleCount}
                              {sub.warnings && sub.warnings.length > 0 && (
                                <span className="warning-icon">
                                  &#9888;
                                  <span className="warning-popup">
                                    {sub.warnings.map((w, i) => (
                                      <span key={i} className="warning-popup-item">
                                        {w}
                                      </span>
                                    ))}
                                  </span>
                                </span>
                              )}
                            </td>
                            <td className="entity-cell subitem-entity">
                              {subIsRepairing && <img className="spinner spinner-sm" src="/polarion/ria/images/progressWheel48.svg" alt="" />}
                              <EntityRef html={sub.fields?.['$_self']?.renderedValue} fallback={sub.entityId} />
                              <span
                                className={`expand-arrow${subHasIssues ? ' clickable' : ''}`}
                                title={subHasIssues ? (subIsExpanded ? 'Collapse' : 'Expand') : ''}
                                onClick={() => subHasIssues && onToggleExpanded(subKey)}
                              >
                                {subIsExpanded ? '\u25B4' : '\u25BE'}
                              </span>
                            </td>
                          </tr>
                          {subIsExpanded && subHasIssues && (
                            <tr className="expand-row">
                              <td colSpan={3}>
                                <IssueList
                                  issues={sub.issues}
                                  selected={subSelected}
                                  repairers={repairers}
                                  hiddenRepairers={hiddenRepairers}
                                  onToggle={(i) => onToggleIssueSelection(subKey, i)}
                                  disabled={batchRepairing || !!sub.revision}
                                  disabledTitle={sub.revision ? REVISIONED_TOOLTIP : undefined}
                                  className="subitem-issue-list"
                                />
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      );
                    })}
                </React.Fragment>
              );
            })}
          </tbody>
        </table>
      ) : (
        <p className="no-issues">No issues found.</p>
      )}

      {result.report && (
        <details className="report-section">
          <summary>Scan Report</summary>
          <pre>{result.report}</pre>
        </details>
      )}
    </div>
  );
}
