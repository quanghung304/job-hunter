import { defineConfig, devices } from '@playwright/test'

/**
 * E2E smoke tests against a running stack (`make e2e`, docs/testing.md). Not part of
 * `make verify`. Base URL: E2E_BASE_URL or the backend serving the built SPA on :8080.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
