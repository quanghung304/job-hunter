import js from '@eslint/js'
import { defineConfig, globalIgnores } from 'eslint/config'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'playwright-report', 'test-results', 'src/api/schema.d.ts']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat.recommended],
    plugins: { 'react-refresh': reactRefresh },
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    rules: {
      // conventions.md: no `any` (use `unknown` + narrowing)
      '@typescript-eslint/no-explicit-any': 'error',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    // shadcn components export variants next to the component.
    files: ['src/components/ui/**/*.tsx', 'src/App.tsx', 'src/test/**'],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
])
