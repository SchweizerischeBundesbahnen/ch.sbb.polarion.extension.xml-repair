import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';

export interface NumericInputHint {
  value: number;
  label: string;
}

interface NumericInputProps {
  value: number;
  defaultValue: number;
  onChange: (val: number) => void;
  hints?: NumericInputHint[];
  hintsLoading?: boolean;
  placeholder?: string;
  allowEmpty?: boolean;
}

export default function NumericInput({
  value,
  defaultValue,
  onChange,
  hints,
  hintsLoading = false,
  placeholder,
  allowEmpty = false,
}: NumericInputProps) {
  const hasHints = hints !== undefined || hintsLoading;
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const wrapRef = useRef<HTMLDivElement>(null);

  const close = () => {
    setOpen(false);
    setFilter('');
  };

  useEffect(() => {
    if (!hasHints) return;
    const handleClick = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) close();
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [hasHints]);

  const handleNumericChange = (raw: string) => {
    const digits = raw.replace(/\D/g, '');
    onChange(digits === '' ? 0 : parseInt(digits, 10));
  };

  const handleBlur = () => {
    if (!allowEmpty && value < 1) onChange(defaultValue);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape' && open) {
      e.stopPropagation();
      close();
      return;
    }
    if (!allowEmpty && e.key === 'Enter' && value < 1) {
      // Pressing Enter in an empty field would submit 0 to the backend,
      // causing invalid requests. The onBlur fallback doesn't help here because blur doesn't fire
      // on Enter-submit. We stop propagation so the parent's Enter handler doesn't fire with the
      // stale value, and restore the default. The next Enter press will submit the valid value.
      e.stopPropagation();
      onChange(defaultValue);
    }
  };

  if (!hasHints) {
    return (
      <input
        type="text"
        inputMode="numeric"
        value={value || ''}
        placeholder={placeholder}
        onChange={(e) => handleNumericChange(e.target.value)}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
      />
    );
  }

  const filteredHints = (hints || []).filter((h) => {
    if (!filter) return true;
    const q = filter.toLowerCase();
    return h.label.toLowerCase().includes(q) || String(h.value).includes(q);
  });

  return (
    <div className="numeric-input-with-hints" ref={wrapRef}>
      <input
        type="text"
        inputMode="numeric"
        className="numeric-input-field"
        value={value || ''}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(e) => handleNumericChange(e.target.value)}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
      />
      {hintsLoading ? (
        <img className="numeric-input-spinner" src="/polarion/ria/images/progressWheel48.svg" alt="" />
      ) : (
        <span
          className="numeric-input-chevron"
          onMouseDown={(e) => {
            // prevent input blur so focus state stays consistent
            e.preventDefault();
            setOpen(!open);
          }}
        >
          {open ? '▴' : '▾'}
        </span>
      )}
      {open && !hintsLoading && (
        <div className="numeric-input-dropdown">
          <input
            className="numeric-input-filter"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.stopPropagation();
                if (filter) setFilter('');
                else close();
              } else if (e.key === 'Enter') {
                // Prevent the parent panel's Enter handler from starting a scan while filtering.
                e.stopPropagation();
              }
            }}
            placeholder="Filter..."
          />
          {filteredHints.length === 0 ? (
            <div className="numeric-input-empty">No matches</div>
          ) : (
            filteredHints.map((h) => (
              <div
                key={h.value}
                className={`numeric-input-item${h.value === value ? ' selected' : ''}`}
                onClick={() => {
                  onChange(h.value);
                  close();
                }}
              >
                <span className="numeric-input-item-value">{h.value}</span>
                <span className="numeric-input-item-label" title={h.label}>
                  {h.label}
                </span>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
