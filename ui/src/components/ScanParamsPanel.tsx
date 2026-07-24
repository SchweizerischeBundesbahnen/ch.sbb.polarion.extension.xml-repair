import type { KeyboardEvent } from 'react';
import type { EntityType, IconSelectOption } from '../types';
import NumericInput from './NumericInput';
import type { NumericInputHint } from './NumericInput';
import SearchableInput from './SearchableInput';
import SearchableSelect from './SearchableSelect';

const QUERY_PLACEHOLDERS: Record<EntityType, string> = {
  WORKITEM: 'e.g. id:PRJID-123',
  DOCUMENT: 'e.g. moduleName:specification',
  COLLECTION: 'e.g. name:specification',
};

interface ScanParamsPanelProps {
  entityType: EntityType;
  entityValue: string;
  combinedEntityOptions: IconSelectOption[];
  onEntityChange: (val: string) => void;
  userQuery: string;
  onUserQueryChange: (val: string) => void;
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
  onEnterKey: () => void;
}

export default function ScanParamsPanel({
  entityType,
  entityValue,
  combinedEntityOptions,
  onEntityChange,
  userQuery,
  onUserQueryChange,
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
  onEnterKey,
}: ScanParamsPanelProps) {
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
        <label>Query</label>
        <input
          type="text"
          value={userQuery}
          onChange={(e) => onUserQueryChange(e.target.value)}
          placeholder={QUERY_PLACEHOLDERS[entityType]}
        />
      </div>

      <details className="advanced-section">
        <summary className="advanced-summary">Advanced</summary>
        <div className="advanced-fields">
          {entityType !== 'COLLECTION' && (
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
            <label htmlFor="hide-valid">Show items with issues only</label>
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
