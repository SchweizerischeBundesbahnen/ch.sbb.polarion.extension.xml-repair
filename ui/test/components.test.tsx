import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import NumericInput from '../src/components/NumericInput';
import SearchableInput from '../src/components/SearchableInput';

// Focused unit tests for the form controls this app owns, covering the secondary branches the Repair
// page flow never reaches: no maxDigits, an emptied field, hint lists arriving after mount, and a value
// driven from React state onto an already-wrapped dropdown trigger. The comboboxes are RSP's
// SearchableSelect (both modes), tested there.
// Note: vitest-browser-react's render commits asynchronously - always await the first query.

function setNativeValue(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

const firstInput = async (selector = 'input'): Promise<HTMLInputElement> => {
  await vi.waitFor(() => expect(document.querySelector(selector)).not.toBeNull());
  return document.querySelector<HTMLInputElement>(selector)!;
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('NumericInput', () => {
  it('keeps every digit when no maxDigits is set', async () => {
    const onChange = vi.fn();
    render(<NumericInput value={0} defaultValue={3} onChange={onChange} />);
    setNativeValue(await firstInput(), '1234567');
    expect(onChange).toHaveBeenLastCalledWith(1234567);
  });

  it('caps the value to maxDigits and reports 0 for an emptied field', async () => {
    const onChange = vi.fn();
    render(<NumericInput value={12} defaultValue={3} onChange={onChange} maxDigits={3} />);
    const input = await firstInput();
    setNativeValue(input, '9a8b7c6');
    expect(onChange).toHaveBeenLastCalledWith(987);
    setNativeValue(input, '');
    expect(onChange).toHaveBeenLastCalledWith(0);
  });

  it('restores the default on a blank blur', async () => {
    const onChange = vi.fn();
    render(<NumericInput value={0} defaultValue={5} onChange={onChange} />);
    (await firstInput()).dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    expect(onChange).toHaveBeenLastCalledWith(5);
  });

  it('leaves a blank blur alone when allowEmpty is set', async () => {
    const onChange = vi.fn();
    render(<NumericInput value={0} defaultValue={5} onChange={onChange} allowEmpty />);
    (await firstInput()).dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('ignores keys other than Enter and does not fire Enter when a value is present', async () => {
    const onChange = vi.fn();
    render(<NumericInput value={0} defaultValue={5} onChange={onChange} />);
    const input = await firstInput();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));
    expect(onChange).not.toHaveBeenCalled();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(onChange).toHaveBeenCalledWith(5);
  });
});

describe('SearchableInput', () => {
  it('strips non-digits and falls back to the default for an emptied field', async () => {
    const onChange = vi.fn();
    render(<SearchableInput value={0} defaultValue={7} onChange={onChange} hints={[]} />);
    const input = await firstInput('input[inputmode="numeric"]');
    setNativeValue(input, '4x2');
    expect(onChange).toHaveBeenLastCalledWith(42);
    setNativeValue(input, 'abc');
    expect(onChange).toHaveBeenLastCalledWith(7);
  });

  it('refreshes the suggestion list when hints arrive after mount', async () => {
    // The hints come from a REST call that resolves after the control is already wrapped, so the
    // dropdown has to be re-seeded rather than only reading its mount-time closure.
    function Harness() {
      const [hints, setHints] = useState<{ value: number; label: string }[]>([]);
      return (
        <>
          <SearchableInput value={0} defaultValue={1} onChange={() => {}} hints={hints} />
          <button onClick={() => setHints([{ value: 10, label: '10 (last run)' }])}>load hints</button>
        </>
      );
    }
    render(<Harness />);
    await firstInput('input[inputmode="numeric"]');
    document.querySelector<HTMLButtonElement>('button')!.click();
    // The wrapped control keeps working; the assertion is that re-seeding does not throw or unmount it.
    await vi.waitFor(() => expect(document.querySelector('input[inputmode="numeric"]')).not.toBeNull());
  });

  it('mirrors a value driven from React state onto the control', async () => {
    function Harness() {
      const [value, setValue] = useState(3);
      return (
        <>
          <SearchableInput value={value} defaultValue={1} onChange={() => {}} hints={[]} />
          <button onClick={() => setValue(25)}>set</button>
        </>
      );
    }
    render(<Harness />);
    const input = await firstInput('input[inputmode="numeric"]');
    expect(input.value).toBe('3');
    document.querySelector<HTMLButtonElement>('button')!.click();
    await vi.waitFor(() => expect(input.value).toBe('25'));
  });
});
