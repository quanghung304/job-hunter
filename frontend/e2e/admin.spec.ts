import { expect, test } from '@playwright/test'

/**
 * Smoke flow 3 of docs/testing.md: "Run pipeline" on the admin page starts a run, the progress
 * banner shows while it runs, and the run completes and appears in the list (the run itself makes
 * no LLM calls, ADR-0010). Needs the running stack with WireMock-served sources.
 */
test('flow 3: admin "Run pipeline" completes', async ({ page }) => {
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'System', level: 1 })).toBeVisible()
  const runs = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Recent pipeline runs' }) })
  const before = await runs.getByRole('listitem').count()

  const run = page.getByRole('button', { name: 'Run pipeline' })
  await expect(run).toBeEnabled({ timeout: 60_000 })
  await run.click()

  await expect(page.getByText('Pipeline started').or(page.getByText('Pipeline is already running'))).toBeVisible()
  await expect(page.getByRole('button', { name: 'Run pipeline' })).toBeEnabled({ timeout: 120_000 })
  await expect(page.getByRole('status', { name: 'Progress' })).toHaveCount(0)
  await expect
    .poll(async () => runs.getByRole('listitem').count(), { timeout: 10_000 })
    .toBeGreaterThanOrEqual(Math.min(before + 1, 10))
  await expect(runs.getByRole('listitem').first()).toContainText(/Manual|Scheduled|Catch-up/)
})
