import React from 'react';

interface OutdatedAttributesPanelProps {
  /** Attribute ids the scan found, sorted. Empty before the first scan. */
  attributes: string[];
  /** How many entities hold each attribute, keyed by attribute id. */
  attributeCounts: Map<string, number>;
  selectedAttributes: Set<string>;
  onToggleAttribute: (id: string) => void;
  onToggleAll: () => void;
  detailsRef: React.RefObject<HTMLDetailsElement | null>;
}

/**
 * The Purge page's counterpart of the "Repairers" block: the attributes the scan actually found filled but no
 * longer defined. Ticking them is what decides which entities the results list shows, so the list can only be
 * built after a scan.
 *
 * It reuses the repairer-block class names on purpose, so both panels stay visually identical.
 */
export default function OutdatedAttributesPanel({
  attributes,
  attributeCounts,
  selectedAttributes,
  onToggleAttribute,
  onToggleAll,
  detailsRef,
}: OutdatedAttributesPanelProps) {
  return (
    <details className="repairers-section attributes-section" ref={detailsRef}>
      <summary className="repairers-summary">
        Outdated attributes
        <span className="repairers-count">
          ({selectedAttributes.size}/{attributes.length} selected)
        </span>
      </summary>
      <div className="repairers-list">
        {attributes.length === 0 ? (
          <p className="attributes-placeholder">Run a scan to see which attributes are filled but not defined.</p>
        ) : (
          <>
            <label className="repairer-item select-all">
              <input type="checkbox" checked={selectedAttributes.size === attributes.length} onChange={onToggleAll} />
              <span>Select All</span>
            </label>
            {attributes.map((attribute) => {
              const isSelected = selectedAttributes.has(attribute);
              const count = attributeCounts.get(attribute) ?? 0;
              return (
                <div key={attribute} className={`repairer-card ${isSelected ? 'active' : ''}`}>
                  <label className="repairer-header">
                    <input type="checkbox" checked={isSelected} onChange={() => onToggleAttribute(attribute)} />
                    <div className="repairer-info">
                      <span className="repairer-name">{attribute}</span>
                      <span className="repairer-desc">
                        filled in {count} {count === 1 ? 'item' : 'items'}
                      </span>
                    </div>
                  </label>
                </div>
              );
            })}
          </>
        )}
      </div>
    </details>
  );
}
