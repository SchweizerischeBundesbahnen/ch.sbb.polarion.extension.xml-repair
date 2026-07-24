import { useEffect, useRef } from 'react';
import { type SearchableDropdownInstance, createSearchableSelect } from '@grigoriev/react-sbb-polarion';
import type { IconSelectOption } from '../types';

interface SearchableSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: IconSelectOption[];
  placeholder?: string;
  allowEmpty?: boolean;
  disabled?: boolean;
  loading?: boolean;
}

// Renders a React-controlled native <select> (carrying each option's icon / icon background / indent
// as data-* attributes and a class) and upgrades it to the shared Polarion SearchableDropdown. The
// dropdown factory is react-sbb-polarion's bundled createSearchableSelect (no runtime fetch), so it
// works in `vite dev` and in tests; preserveOptionClasses (a factory default) mirrors the `indented`
// class onto nested-subtype options. The <select> stays the source of truth; the dropdown mirrors the
// selection back and dispatches `change`. Falls back to the plain <select> if creation throws.
export default function SearchableSelect({
  value,
  onChange,
  options,
  placeholder = 'Select…',
  allowEmpty = false,
  disabled = false,
  loading = false,
}: SearchableSelectProps) {
  const selectRef = useRef<HTMLSelectElement>(null);
  const sdRef = useRef<SearchableDropdownInstance | null>(null);

  useEffect(() => {
    const element = selectRef.current;
    if (!element) return;
    try {
      sdRef.current = createSearchableSelect(element, { allowEmpty, placeholder });
      sdRef.current?.selectValue(value);
    } catch {
      /* keep the native <select> */
    }
    return () => {
      if (sdRef.current) {
        sdRef.current.destroy();
        sdRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keep the dropdown trigger in sync when the value is driven from React state (option changes are
  // picked up by SearchableDropdown's own MutationObserver).
  useEffect(() => {
    sdRef.current?.selectValue(value);
  }, [value]);

  return (
    <select ref={selectRef} value={value} disabled={disabled || loading} onChange={(e) => onChange(e.target.value)}>
      {allowEmpty && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option
          key={o.id}
          value={o.id}
          data-icon={o.iconURL || undefined}
          data-icon-bg={o.iconBg || undefined}
          className={o.indent ? 'indented' : undefined}
        >
          {o.name}
        </option>
      ))}
    </select>
  );
}
