import { useEffect, useRef } from 'react';
import type { IconSelectOption } from '../types';

// The shared generic combobox factory (createSearchableSelect) is a vanilla-JS module served at
// runtime from the embedded generic.app (not an npm dependency). Its URL is derived from the app's
// own location so there is no hardcoded /<ext>-app/ segment; @vite-ignore keeps the bundler from
// resolving it at build time.
const GENERIC_MODULES = '/ui/generic/js/modules/';

interface SearchableSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: IconSelectOption[];
  placeholder?: string;
  allowEmpty?: boolean;
  disabled?: boolean;
  loading?: boolean;
}

// Drop-in replacement for the old bespoke IconSelect: renders a React-controlled native <select>
// (carrying each option's icon / icon background / indent as data-* attributes and a class) and
// upgrades it to the shared Polarion SearchableDropdown at runtime, so every combobox in the app is
// the one generic component. The <select> stays the source of truth; the dropdown mirrors the
// selection back and dispatches `change`. Falls back to the plain <select> if the module can't load
// (e.g. a vite dev server outside Polarion).
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
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const sdRef = useRef<any>(null);

  useEffect(() => {
    let cancelled = false;
    const element = selectRef.current;
    const base = window.location.pathname.replace(/\/ui\/.*$/, GENERIC_MODULES);
    import(/* @vite-ignore */ base + 'searchableSelect.js')
      .then((module) => {
        if (cancelled || !element) return;
        // preserveOptionClasses (a factory default) mirrors each <option>'s class (e.g. `indented`
        // for nested subtypes) onto the rendered option so a CSS rule can indent it.
        sdRef.current = module.createSearchableSelect(element, { allowEmpty, placeholder });
      })
      .catch(() => { /* keep the native <select> */ });
    return () => {
      cancelled = true;
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
    if (sdRef.current?.selectValue) {
      sdRef.current.selectValue(value);
    }
  }, [value]);

  return (
    <select
      ref={selectRef}
      value={value}
      disabled={disabled || loading}
      onChange={(e) => onChange(e.target.value)}
    >
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
