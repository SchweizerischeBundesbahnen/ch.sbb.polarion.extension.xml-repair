import type { KeyboardEvent } from 'react';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { EntityType, FilterMode, IconSelectOption } from '../types';
import NumericInput from './NumericInput';
import type { NumericInputHint } from './NumericInput';
import SearchableInput from './SearchableInput';

const QUERY_PLACEHOLDERS: Record<EntityType, string> = {
  WORKITEM: 'e.g. id:PRJID-123',
  DOCUMENT: 'e.g. moduleName:specification',
  COLLECTION: 'e.g. name:specification',
};

// Label and "nothing picked" placeholder of the selection row. Work items have no selection mode - a
// project holds far too many of them for a dropdown - so they are absent here.
const SELECTION_LABELS: Partial<Record<EntityType, { label: string; placeholder: string }>> = {
  DOCUMENT: { label: 'Documents', placeholder: 'All documents' },
  COLLECTION: { label: 'Collections', placeholder: 'All collections' },
};

// Two 12px glyphs for the mode toggle, inlined so the button needs no Polarion-served icon: a list
// (switch to selection) and a magnifier (switch to query).
const LIST_ICON = (
  <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true" focusable="false">
    <path
      d="M1 2h1.5M1 6h1.5M1 10h1.5M4.5 2H11M4.5 6H11M4.5 10H11"
      stroke="currentColor"
      strokeWidth="1.4"
      fill="none"
      strokeLinecap="round"
    />
  </svg>
);

const QUERY_ICON = (
  <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true" focusable="false">
    <circle cx="5" cy="5" r="3.6" stroke="currentColor" strokeWidth="1.4" fill="none" />
    <path d="M8 8l3 3" stroke="currentColor" strokeWidth="1.4" fill="none" strokeLinecap="round" />
  </svg>
);

interface ScanParamsPanelProps {
  entityType: EntityType;
  entityValue: string;
  combinedEntityOptions: IconSelectOption[];
  onEntityChange: (val: string) => void;
  filterMode: FilterMode;
  onFilterModeChange: (mode: FilterMode) => void;
  entityOptions: IconSelectOption[];
  entitiesLoading: boolean;
  selectedEntities: string[];
  onSelectedEntitiesChange: (values: string[]) => void;
  userQuery: string;
  onUserQueryChange: (val: string) => void;
  /** False on a page that cannot scan a revision, which removes the row rather than disabling it. */
  showRevision?: boolean;
  revision: number;
  onRevisionChange: (val: number) => void;
  revisionHints: NumericInputHint[];
  revisionLoading: boolean;
  sort: string;
  onSortChange: (val: string) => void;
  limit: number;
  onLimitChange: (val: number) => void;
  timeout: number;
  onTimeoutChange: (val: number) => void;
  hideValid: boolean;
  onHideValidChange: (val: boolean) => void;
  /** What "valid" means on this page: issues on Scan & Repair, outdated attributes on Purge. */
  hideValidLabel?: string;
  onEnterKey: () => void;
}

export default function ScanParamsPanel({
  entityType,
  entityValue,
  combinedEntityOptions,
  onEntityChange,
  filterMode,
  onFilterModeChange,
  entityOptions,
  entitiesLoading,
  selectedEntities,
  onSelectedEntitiesChange,
  userQuery,
  onUserQueryChange,
  showRevision = true,
  revision,
  onRevisionChange,
  revisionHints,
  revisionLoading,
  sort,
  onSortChange,
  limit,
  onLimitChange,
  timeout,
  onTimeoutChange,
  hideValid,
  onHideValidChange,
  hideValidLabel = 'Show items with issues only',
  onEnterKey,
}: ScanParamsPanelProps) {
  // `selection` is absent for entity types without a picker (work items): no toggle button, the query
  // row is all there is.
  const selection = SELECTION_LABELS[entityType];
  const selectionActive = !!selection && filterMode === 'SELECTION';

  return (
    <div
      className="form-section"
      onKeyDown={(e: KeyboardEvent) => {
        const target = e.target as HTMLInputElement;
        if (e.key === 'Enter' && target.tagName === 'INPUT' && target.type === 'text') {
          onEnterKey();
        }
      }}
    >
      <div className="form-row">
        <label>Entity Type</label>
        <SearchableSelect
          value={entityValue}
          onChange={onEntityChange}
          options={combinedEntityOptions}
          allowEmpty={false}
        />
      </div>

      <div className="form-row">
        <label htmlFor={selectionActive ? undefined : 'user-query'}>
          {selectionActive ? selection.label : 'Query'}
        </label>
        <div className="filter-control">
          {selectionActive ? (
            <SearchableSelect
              multiple
              value={selectedEntities}
              onChange={onSelectedEntitiesChange}
              options={entityOptions}
              placeholder={entitiesLoading ? 'Loading…' : selection.placeholder}
              loading={entitiesLoading}
            />
          ) : (
            <input
              id="user-query"
              type="text"
              value={userQuery}
              onChange={(e) => onUserQueryChange(e.target.value)}
              placeholder={QUERY_PLACEHOLDERS[entityType]}
            />
          )}
          {selection && (
            <button
              type="button"
              className="filter-mode-toggle"
              onClick={() => onFilterModeChange(selectionActive ? 'QUERY' : 'SELECTION')}
              title={
                selectionActive ? 'Switch to Lucene query' : `Switch to ${selection.label.toLowerCase()} selection`
              }
              aria-label={
                selectionActive ? 'Switch to Lucene query' : `Switch to ${selection.label.toLowerCase()} selection`
              }
            >
              {selectionActive ? QUERY_ICON : LIST_ICON}
            </button>
          )}
        </div>
      </div>

      <details className="advanced-section">
        <summary className="advanced-summary">Advanced</summary>
        <div className="advanced-fields">
          {showRevision && entityType !== 'COLLECTION' && (
            <div className="form-row">
              <label>
                Revision/Baseline
                <span
                  className="help-icon"
                  title="Selecting specific revision other than HEAD will block the ability to repair issues"
                >
                  ?
                </span>
              </label>
              <SearchableInput
                value={revision}
                defaultValue={0}
                onChange={onRevisionChange}
                hints={revisionHints}
                hintsLoading={revisionLoading}
                placeholder="HEAD"
              />
            </div>
          )}
          <div className="form-row">
            <label>Sort By</label>
            <input
              type="text"
              value={sort}
              onChange={(e) => onSortChange(e.target.value)}
              placeholder="e.g. created or ~id"
            />
          </div>
          <div className="form-row">
            <label>Show Top Rows</label>
            <NumericInput value={limit} defaultValue={100} onChange={onLimitChange} />
          </div>
          <div className="form-row">
            <label>Scan time limit, seconds</label>
            <NumericInput value={timeout} defaultValue={60} onChange={onTimeoutChange} />
          </div>
          <div className="form-row">
            <label htmlFor="hide-valid">{hideValidLabel}</label>
            <input
              id="hide-valid"
              type="checkbox"
              checked={hideValid}
              onChange={(e) => onHideValidChange(e.target.checked)}
            />
          </div>
        </div>
      </details>
    </div>
  );
}
