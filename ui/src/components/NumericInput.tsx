import type { KeyboardEvent } from 'react';

export interface NumericInputHint {
  value: number;
  label: string;
}

interface NumericInputProps {
  value: number;
  defaultValue: number;
  onChange: (val: number) => void;
  placeholder?: string;
  allowEmpty?: boolean;
  maxDigits?: number;
}

// Plain numeric text field: digits only, optionally capped to `maxDigits`, restoring `defaultValue`
// on a blank blur/Enter unless `allowEmpty`. Kept identical across the React SPAs (no vanilla core to
// share, so it stays an aligned copy rather than a generic module).
export default function NumericInput({
  value,
  defaultValue,
  onChange,
  placeholder,
  allowEmpty = false,
  maxDigits,
}: NumericInputProps) {
  const handleChange = (raw: string) => {
    let digits = raw.replace(/\D/g, '');
    if (maxDigits) {
      digits = digits.slice(0, maxDigits);
    }
    onChange(digits === '' ? 0 : parseInt(digits, 10));
  };

  const handleBlur = () => {
    if (!allowEmpty && value < 1) {
      onChange(defaultValue);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!allowEmpty && e.key === 'Enter' && value < 1) {
      // Enter on an empty field would submit 0; restore the default and stop the parent's Enter
      // handler from firing with the stale value.
      e.stopPropagation();
      onChange(defaultValue);
    }
  };

  return (
    <input
      type="text"
      inputMode="numeric"
      value={value || ''}
      placeholder={placeholder}
      onChange={(e) => handleChange(e.target.value)}
      onBlur={handleBlur}
      onKeyDown={handleKeyDown}
    />
  );
}
