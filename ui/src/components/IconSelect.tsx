import { useEffect, useRef, useState } from 'react';
import type { IconSelectOption } from '../types';

interface IconSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: IconSelectOption[];
  placeholder?: string;
  allowEmpty?: boolean;
}

export default function IconSelect({
  value,
  onChange,
  options,
  placeholder = 'Select...',
  allowEmpty = true,
}: IconSelectProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.id === value);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  return (
    <div className="icon-select" ref={ref}>
      <div className="icon-select-trigger" onClick={() => setOpen(!open)}>
        {selected ? (
          <span className="icon-select-option">
            {selected.iconURL && <img src={selected.iconURL} alt="" className="icon-select-icon" />}
            {selected.name}
          </span>
        ) : (
          <span className="icon-select-placeholder">{placeholder}</span>
        )}
        <span className="icon-select-arrow">{open ? '▴' : '▾'}</span>
      </div>
      {open && (
        <div className="icon-select-dropdown">
          {allowEmpty && (
            <div
              className={`icon-select-item ${!value ? 'selected' : ''}`}
              onClick={() => {
                onChange('');
                setOpen(false);
              }}
            >
              {placeholder}
            </div>
          )}
          {options.map((o) => (
            <div
              key={o.id}
              className={`icon-select-item ${o.id === value ? 'selected' : ''}${o.indent ? ' indented' : ''}`}
              onClick={() => {
                onChange(o.id);
                setOpen(false);
              }}
            >
              {o.iconURL && <img src={o.iconURL} alt="" className="icon-select-icon" />}
              {o.name}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
