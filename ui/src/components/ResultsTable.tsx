import React, { memo } from 'react';
import type { Repairer, ScanEntity, ScanResult } from '../types';
import IssueList from './IssueList';

// Memoized to prevent DOM re-creation on parent re-renders (dangerouslySetInnerHTML causes blink otherwise)
const EntityRef = memo(({ html, fallback }: { html?: string; fallback: string }) =>
  html ? <span className="entity-ref" dangerouslySetInnerHTML={{ __html: html }} /> : <>{fallback}</>,
);

const hasSubitems = (item: ScanEntity): boolean => item.subitems && item.subitems.length > 0;
const itemKey = (item: ScanEntity): string => `${item.projectId}-${item.space || ''}-${item.entityId}`;
const subitemKey = (parentKey: string, sub: ScanEntity): string =>
  `${parentKey}/${sub.projectId}-${sub.space || ''}-${sub.entityId}`;
const totalIssuesForItem = (item: ScanEntity): number =>
  hasSubitems(item) ? item.subitems.reduce((sum, sub) => sum + sub.issues.length, 0) : item.issues.length;

const REVISIONED_TOOLTIP = 'Items at a specific revision cannot be repaired';

interface ResultsTableProps {
  result: ScanResult;
  repairers: Repairer[];
  selectedIssues: Map<string, Set<number>>;
  expandedRows: Set<string>;
  repairingEntity: string | null;
  batchRepairing: boolean;
  onToggleEntitySelection: (entityKey: string, totalIssues: number) => void;
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
  const itemsWithIssues = result.items.filter((item) => {
    if (hasSubitems(item)) return item.subitems.some((sub) => sub.issues.length > 0);
    return item.issues.length > 0;
  });

  return (
    <div className="results-section">
      <h3>Results ({result.items.length} entities)</h3>

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
                          if (sub.issues.length > 0) {
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
            {result.items.map((item) => {
              const entityKey = itemKey(item);
              const isCollection = hasSubitems(item);
              const issueCount = isCollection ? totalIssuesForItem(item) : item.issues.length;
              const hasIssues = issueCount > 0;
              const isExpanded = expandedRows.has(entityKey);
              const isRepairing = repairingEntity === entityKey;

              let allSelected;
              let someSelected = false;
              let selectableInCollection = false;
              if (isCollection) {
                const subs = item.subitems.filter((sub) => sub.issues.length > 0 && !sub.repaired && !sub.revision);
                selectableInCollection = subs.length > 0;
                const allSubsSelected =
                  subs.length > 0 &&
                  subs.every((sub) => {
                    const sel = selectedIssues.get(subitemKey(entityKey, sub));
                    return sel && sel.size === sub.issues.length;
                  });
                const anySubSelected = subs.some(
                  (sub) => (selectedIssues.get(subitemKey(entityKey, sub))?.size || 0) > 0,
                );
                allSelected = allSubsSelected;
                someSelected = anySubSelected && !allSubsSelected;
              } else {
                const selected = selectedIssues.get(entityKey) || new Set();
                allSelected = hasIssues && selected.size === item.issues.length;
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
                              : onToggleEntitySelection(entityKey, item.issues.length)
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
                      {isRepairing && <span className="spinner spinner-sm" />}
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

                  {isExpanded && !isCollection && (
                    <tr className="expand-row">
                      <td colSpan={3}>
                        <IssueList
                          issues={item.issues}
                          selected={selectedIssues.get(entityKey) || new Set()}
                          repairers={repairers}
                          onToggle={(i) => onToggleIssueSelection(entityKey, i)}
                          disabled={batchRepairing || itemRevisioned}
                          disabledTitle={itemRevisioned ? REVISIONED_TOOLTIP : undefined}
                        />
                      </td>
                    </tr>
                  )}

                  {isExpanded &&
                    isCollection &&
                    item.subitems.map((sub) => {
                      const subKey = subitemKey(entityKey, sub);
                      const subHasIssues = sub.issues.length > 0;
                      const subSelected = selectedIssues.get(subKey) || new Set();
                      const subAllSelected = subHasIssues && subSelected.size === sub.issues.length;
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
                                  onChange={() => onToggleEntitySelection(subKey, sub.issues.length)}
                                  disabled={batchRepairing || !subHasIssues || !!sub.revision}
                                  title={sub.revision ? REVISIONED_TOOLTIP : undefined}
                                />
                              )}
                              {sub.repaired && <span className="fixed-badge">&#10003;</span>}
                            </td>
                            <td
                              className={`col-issues${subHasIssues ? ' clickable' : ''}`}
                              style={sub.issues.length > 0 ? { color: '#000', fontWeight: 'bold' } : { color: '#ccc' }}
                              title={subHasIssues ? (subIsExpanded ? 'Hide details' : 'Click to see details') : ''}
                              onClick={() => subHasIssues && onToggleExpanded(subKey)}
                            >
                              {sub.issues.length}
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
                              {subIsRepairing && <span className="spinner spinner-sm" />}
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
                          {subIsExpanded && (
                            <tr className="expand-row">
                              <td colSpan={3}>
                                <IssueList
                                  issues={sub.issues}
                                  selected={subSelected}
                                  repairers={repairers}
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
