import { expect, type Page, test } from '@playwright/test'

/**
 * Smoke flow 5 of docs/testing.md: change "Max hours per week" on the Profile page, save, the toast
 * reports the re-checked jobs, a reload shows the new value. This rewrites config/profile.yaml of
 * the running stack (canonical form, previous file in profile.yaml.bak); the old value is saved
 * back at the end.
 */

async function setMaxHours(page: Page, value: string) {
  const field = page.getByLabel(/Max hours per week/)
  await field.fill(value)
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText(/^Profile saved · eligibility re-checked for \d+ jobs?$/)).toBeVisible()
  await expect(page.getByText('No changes')).toBeVisible()
}

test('flow 5: Profile page saves max hours; a reload shows the new value', async ({ page }) => {
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: /Your profile/, level: 1 })).toBeVisible()
  const field = page.getByLabel(/Max hours per week/)
  const original = await field.inputValue()
  const next = original === '37' ? '36' : '37'

  await setMaxHours(page, next)
  await page.reload()
  await expect(page.getByLabel(/Max hours per week/)).toHaveValue(next)

  await setMaxHours(page, original)
  await page.reload()
  await expect(page.getByLabel(/Max hours per week/)).toHaveValue(original)
})
