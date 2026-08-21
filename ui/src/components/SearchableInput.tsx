import { useEffect, useRef } from 'react';
import { type SearchableDropdownInstance, createEditableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { NumericInputHint } from './NumericInput';

interface SearchableInputProps {
  value: number;
  defaultValue: number;
  onChange: (val: number) => void;
  hints?: NumericInputHint[];
  hintsLoading?: boolean;
  placeholder?: string;
}

// Editable (free-text) generic SearchableDropdown wrapping a numeric <input>: the user can type any
// revision number OR pick one from the hint list, both filtered as they type. The factory is
// react-sbb-polarion's bundled createEditableSelect (no runtime fetch), so it works in `vite dev` and
// in tests. The <input> stays React-controlled; SearchableDropdown mirrors the committed value onto it
// and fires `change`, which drives React's onChange.
export default function SearchableInput({ value, defaultValue, onChange, hints, placeholder }: SearchableInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const sdRef = useRef<SearchableDropdownInstance | null>(null);
  // Latest hints, so the mount effect seeds the dropdown with whatever loaded by then - not the
  // possibly-empty value captured in its [] closure.
  const hintsRef = useRef(hints);
  hintsRef.current = hints;

  const toItems = (hs?: NumericInputHint[]) => (hs || []).map((h) => ({ value: String(h.value), label: h.label }));

  useEffect(() => {
    const element = inputRef.current;
    if (!element) return;
    try {
      sdRef.current = createEditableSelect(element, {
        inputFilter: (v: string) => v.replace(/\D/g, ''),
        placeholder,
        items: toItems(hintsRef.current),
      });
    } catch {
      /* keep the native <input> */
    }
    return () => {
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
    const sd = sdRef.current;
    if (!sd) return;
    sd.items = toItems(hints);
    // If the popup is open when hints arrive, re-filter it live so they appear immediately -
    // dispatching 'input' re-renders the open list without closing it (unlike refresh()).
    if (sd.isOpen && sd.trigger) {
      sd.trigger.dispatchEvent(new Event('input'));
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
