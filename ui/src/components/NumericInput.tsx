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
      onBlur={() => { if (value < 1) onChange(defaultValue); }}
    />
  );
}
