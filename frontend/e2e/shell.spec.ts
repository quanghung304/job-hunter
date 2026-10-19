import { expect, test } from '@playwright/test'

test('the shell loads with the board and the profile from /api/meta', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByText('Job Hunter').first()).toBeVisible()
  await expect(page.getByRole('link', { name: 'Board' })).toHaveAttribute('aria-current', 'page')
  await expect(page.getByTestId('profile-id')).not.toHaveText('…')
})

test('a deep link survives a reload (SPA fallback)', async ({ page }) => {
  await page.goto('/jobs/1')
  await page.reload()

  await expect(page.getByRole('heading', { name: 'Board' })).toBeAttached()
})
