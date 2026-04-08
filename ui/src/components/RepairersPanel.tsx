import React from 'react';
import type { Repairer } from '../types';

interface RepairersPanelProps {
  repairers: Repairer[];
  selectedRepairers: string[];
  repairerConfigs: Record<string, Record<string, boolean>>;
  onToggleRepairer: (id: string) => void;
  onToggleAll: () => void;
  onUpdateConfig: (repairerId: string, settingId: string, value: boolean) => void;
  detailsRef: React.RefObject<HTMLDetailsElement | null>;
}

export default function RepairersPanel({
  repairers,
  selectedRepairers,
  repairerConfigs,
  onToggleRepairer,
  onToggleAll,
  onUpdateConfig,
  detailsRef,
}: RepairersPanelProps) {
  return (
    <details className="repairers-section" ref={detailsRef}>
      <summary className="repairers-summary">
        Repairers
        <span className="repairers-count">
          ({selectedRepairers.length}/{repairers.length} selected)
        </span>
      </summary>
      <div className="repairers-list">
        <label className="repairer-item select-all">
          <input
            type="checkbox"
            checked={selectedRepairers.length === repairers.length && repairers.length > 0}
            onChange={onToggleAll}
          />
          <span>Select All</span>
        </label>
        {repairers.map((r) => {
          const isSelected = selectedRepairers.includes(r.id);
          return (
            <div key={r.id} className={`repairer-card ${isSelected ? 'active' : ''}`}>
              <label className="repairer-header">
                <input type="checkbox" checked={isSelected} onChange={() => onToggleRepairer(r.id)} />
                <div className="repairer-info">
                  <span className="repairer-name">{r.name}</span>
                  <span className="repairer-desc">{r.description}</span>
                </div>
              </label>
              {isSelected && r.configs.length > 0 && (
                <div className="repairer-settings">
                  {r.configs.map((c) => (
                    <label key={c.key} className="repairer-setting">
                      <input
                        type="checkbox"
                        checked={repairerConfigs[r.id]?.[c.key] ?? c.defaultValue}
                        onChange={(e) => onUpdateConfig(r.id, c.key, e.target.checked)}
                      />
                      <span>{c.description}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </details>
  );
}
