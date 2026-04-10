interface NumericInputProps {
  value: number;
  defaultValue: number;
  onChange: (val: number) => void;
}

export default function NumericInput({ value, defaultValue, onChange }: NumericInputProps) {
  return (
    <input
      type="text"
      inputMode="numeric"
      value={value || ''}
      onChange={(e) => {
        const raw = e.target.value.replace(/\D/g, '');
        onChange(raw === '' ? 0 : parseInt(raw));
      }}
      onBlur={() => {
        if (value < 1) onChange(defaultValue);
      }}
      // Pressing Enter in an empty field would submit 0 to the backend,
      // causing invalid requests. The onBlur fallback doesn't help here because blur doesn't fire
      // on Enter-submit. We stop propagation so the parent's Enter handler doesn't fire with the
      // stale value, and restore the default. The next Enter press will submit the valid value.
      onKeyDown={(e) => {
        if (e.key === 'Enter' && value < 1) {
          e.stopPropagation();
          onChange(defaultValue);
        }
      }}
    />
  );
}
