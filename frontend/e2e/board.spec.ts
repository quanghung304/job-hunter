import { expect, type Page, test } from '@playwright/test'

/**
 * Smoke flows 1 and 2 of docs/testing.md against a running stack with jobs in the default
 * view (run the pipeline first). Each flow changes data, so it skips when the board is empty.
 */

async function firstJob(page: Page) {
  await page.goto('/')
  const list = page.getByRole('list', { name: 'Jobs' })
  const empty = page.getByText('Inbox zero')
  await expect(list.or(empty)).toBeVisible()
  test.skip(await empty.isVisible(), 'the default board is empty; run the pipeline first')
  return list.getByRole('listitem').first()
}

test('flow 1: the board loads and dismiss hides a job, also after a reload', async ({ page }) => {
  const row = await firstJob(page)
  const title = (await row.getByRole('link').first().getAttribute('href')) ?? ''

  await row.getByRole('button', { name: 'Dismiss' }).click()

  await expect(page.locator(`a[href="${title}"]`)).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Undo' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('list', { name: 'Jobs' }).or(page.getByText('Inbox zero'))).toBeVisible()
  await expect(page.locator(`a[href="${title}"]`)).toHaveCount(0)
})

test('flow 2: open a job, change its status, add a note; a reload keeps both', async ({ page }) => {
  const row = await firstJob(page)
  await row.getByRole('link').first().click()
  const detail = page.getByRole('article')
  await expect(detail.getByRole('heading', { level: 1 })).toBeVisible()

  const shortlist = detail.getByRole('button', { name: /^Shortlist/ })
  test.skip(!(await shortlist.isVisible()), 'the first job cannot be shortlisted')
  await shortlist.click()
  await expect(detail.getByText('Shortlisted').first()).toBeVisible()

  const note = `smoke ${Date.now()}`
  await detail.getByRole('textbox', { name: 'Notes' }).fill(note)
  await expect(detail.getByText('Saved · just now')).toBeVisible({ timeout: 5000 })

  await page.reload()
  await expect(page.getByRole('article').getByRole('textbox', { name: 'Notes' })).toHaveValue(note)
  await expect(page.getByRole('article').getByText('Shortlisted').first()).toBeVisible()
  await expect(page.getByRole('article').getByText('New → Shortlisted')).toBeVisible()
})
