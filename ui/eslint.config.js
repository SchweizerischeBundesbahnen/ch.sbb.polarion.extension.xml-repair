import js from '@eslint/js';
import { polarionEslintConfig } from '@sbb-polarion/react-sbb-polarion/eslint-config';
import globals from 'globals';

// The shared setup of the SBB Polarion React apps (TypeScript, React hooks, jsx-a11y, Prettier), plus
// this extension's plain JS tooling.
export default polarionEslintConfig({
  ignores: ['node', '.vite', 'test/expected', 'test/__diff__', 'test/__screenshots__', '.vitest'],
  configs: [
    // The repairer and attribute cards name their checkbox three levels down the label
    // (label > .repairer-info > .repairer-name); the default depth of 2 misses that text.
    {
      files: ['src/**/*.{ts,tsx}'],
      rules: { 'jsx-a11y/label-has-associated-control': ['error', { depth: 3 }] },
    },
    // Plain JS/ESM (this config, vite.config.js, the docker-test wrapper).
    {
      files: ['**/*.{js,mjs}'],
      extends: [js.configs.recommended],
      languageOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        globals: { ...globals.node },
      },
    },
  ],
});
