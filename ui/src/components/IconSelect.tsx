import { useEffect, useRef, useState } from 'react';
import type { IconSelectOption } from '../types';

interface IconSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: IconSelectOption[];
  placeholder?: string;
  allowEmpty?: boolean;
  disabled?: boolean;
  loading?: boolean;
}

export default function IconSelect({
  value,
  onChange,
  options,
  placeholder = 'Select...',
  allowEmpty = true,
  disabled = false,
  loading = false,
}: IconSelectProps) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const selected = options.find((o) => o.id === value);
  const filtered = options.filter((o) => o.name.toLowerCase().includes(filter.toLowerCase()));

  const close = () => {
    setOpen(false);
    setFilter('');
  };

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  return (
    <div className={`icon-select${disabled ? ' disabled' : ''}`} ref={ref}>
      <div className="icon-select-trigger" onClick={() => !disabled && (open ? close() : setOpen(true))}>
        {selected ? (
          <span className="icon-select-option" title={selected.name}>
            {selected.iconURL && (
              <img
                src={selected.iconURL}
                alt=""
                className="icon-select-icon"
                style={selected.iconBg ? { background: selected.iconBg, padding: '2px' } : undefined}
              />
            )}
            <span className="icon-select-item-text">{selected.name}</span>
          </span>
        ) : (
          <span className="icon-select-placeholder">{placeholder}</span>
        )}
        {loading ? (
          <span className="icon-select-spinner" />
        ) : (
          <span className="icon-select-arrow">{open ? '▴' : '▾'}</span>
        )}
      </div>
      {open && (
        <div className="icon-select-dropdown">
          <input
            ref={inputRef}
            className="icon-select-filter"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                e.stopPropagation();
                setFilter('');
              } else if (e.key === 'Enter') {
                // Prevent the parent panel's Enter handler from starting a scan while filtering.
                e.stopPropagation();
              }
            }}
            placeholder="Filter..."
          />
          {allowEmpty && (
            <div
              className={`icon-select-item ${!value ? 'selected' : ''}`}
              onClick={() => {
                onChange('');
                close();
              }}
            >
              {placeholder}
            </div>
          )}
          {filtered.map((o) => (
            <div
              key={o.id}
              className={`icon-select-item ${o.id === value ? 'selected' : ''}${o.indent ? ' indented' : ''}`}
              onClick={() => {
                onChange(o.id);
                close();
              }}
            >
              {o.iconURL && (
                <img
                  src={o.iconURL}
                  alt=""
                  className="icon-select-icon"
                  style={o.iconBg ? { background: o.iconBg, padding: '2px' } : undefined}
                />
              )}
              <span className="icon-select-item-text" title={o.name}>
                {o.name}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
