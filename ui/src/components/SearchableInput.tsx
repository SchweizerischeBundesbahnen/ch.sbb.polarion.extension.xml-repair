import { useEffect, useRef } from 'react';
import type { NumericInputHint } from './NumericInput';

// Derived from the app's own location so there is no hardcoded /<ext>-app/ segment.
const GENERIC_MODULES = '/ui/generic/js/modules/';

interface SearchableInputProps {
  value: number;
  defaultValue: number;
  onChange: (val: number) => void;
  hints?: NumericInputHint[];
  hintsLoading?: boolean;
  placeholder?: string;
}

// Editable (free-text) generic SearchableDropdown wrapping a numeric <input>: the user can type any
// revision number OR pick one from the hint list, both filtered as they type. Replaces the bespoke
// NumericInput-with-hints. The <input> stays React-controlled; SearchableDropdown mirrors the
// committed value onto it and fires `change`, which drives React's onChange.
export default function SearchableInput({
  value,
  defaultValue,
  onChange,
  hints,
  placeholder,
}: SearchableInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const sdRef = useRef<any>(null);

  const toItems = (hs?: NumericInputHint[]) => (hs || []).map((h) => ({ value: String(h.value), label: h.label }));

  useEffect(() => {
    let cancelled = false;
    const element = inputRef.current;
    const base = window.location.pathname.replace(/\/ui\/.*$/, GENERIC_MODULES);
    import(/* @vite-ignore */ base + 'searchableSelect.js')
      .then((module) => {
        if (cancelled || !element) return;
        sdRef.current = module.createEditableSelect(element, {
          inputFilter: (v: string) => v.replace(/\D/g, ''),
          placeholder,
          items: toItems(hints),
        });
      })
      .catch(() => { /* keep the native <input> */ });
    return () => {
      cancelled = true;
      if (sdRef.current) {
        sdRef.current.destroy();
        sdRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Refresh the suggestion list when hints load/change (the wrapped element is an <input>, so there
  // are no <option> children for SearchableDropdown to observe).
  useEffect(() => {
    if (sdRef.current) {
      // Update the suggestions for the next open (refresh() would close an open popup, so we do not
      // call it here — the list is small and the user reopens to see freshly-loaded hints).
      sdRef.current.items = toItems(hints);
    }
  }, [hints]);

  // Keep the editable trigger in sync when the value is driven from React state.
  useEffect(() => {
    const sd = sdRef.current;
    if (sd && sd.trigger) {
      sd.trigger.value = value ? String(value) : '';
    }
  }, [value]);

  return (
    <input
      ref={inputRef}
      type="text"
      inputMode="numeric"
      value={value || ''}
      placeholder={placeholder}
      onChange={(e) => {
        const digits = e.target.value.replace(/\D/g, '');
        onChange(digits === '' ? defaultValue : parseInt(digits, 10));
      }}
    />
  );
}
