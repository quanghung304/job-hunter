import { expect, type Page, test } from '@playwright/test'

/**
 * Smoke flows 1, 2 and 4 of docs/testing.md against a running stack with jobs in the default
 * view (run the pipeline first). Each flow changes data, so it skips when the board is too small.
 */

/** Opens the grid (optionally a page) and returns its list; skips when it has no cards. */
async function grid(page: Page, search = '') {
  await page.goto(`/${search}`)
  const list = page.getByRole('list', { name: 'Jobs' })
  const empty = page.getByText('Inbox zero')
  await expect(list.or(empty)).toBeVisible()
  test.skip(await empty.isVisible(), 'the default board is empty; run the pipeline first')
  return list
}

/** The `/jobs/<id>` path of a card's title link. */
async function jobPath(card: ReturnType<Page['getByRole']>) {
  const href = (await card.getByRole('link').first().getAttribute('href')) ?? ''
  return href.split('?')[0]
}

test('flow 1: the grid shows up to 12 cards; dismiss hides a job, also after a reload', async ({ page }) => {
  const list = await grid(page)
  const cards = list.getByRole('article')
  expect(await cards.count()).toBeLessThanOrEqual(12)
  await expect(page.getByText(/^Showing \d+–\d+ of \d+ jobs?$/)).toBeVisible()

  const first = cards.first()
  const path = await jobPath(first)
  await first.getByRole('button', { name: 'Dismiss' }).click()

  await expect(page.locator(`a[href^="${path}?"], a[href="${path}"]`)).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Undo' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('list', { name: 'Jobs' }).or(page.getByText('Inbox zero'))).toBeVisible()
  await expect(page.locator(`a[href^="${path}?"], a[href="${path}"]`)).toHaveCount(0)
})

test('flow 2: open a job from page 2; the list holds pages 1–2, Load more keeps the scroll; status and note persist', async ({
  page,
}) => {
  const list = await grid(page, '?page=2')
  test.skip(!(await page.getByRole('button', { name: 'Page 2' }).isVisible()), 'needs at least 13 jobs')
  const card = list.getByRole('article').first()
  const path = await jobPath(card)
  await card.getByRole('link').first().click()

  const detail = page.getByRole('article').filter({ has: page.getByRole('heading', { level: 1 }) })
  await expect(detail.getByRole('heading', { level: 1 })).toBeVisible()
  const panel = page.getByRole('region', { name: 'Job list' })
  const selected = panel.locator(`a[aria-current="true"][href^="${path}?"]`)
  await expect(selected).toBeInViewport()
  expect(await panel.getByRole('list', { name: 'Jobs' }).getByRole('article').count()).toBeGreaterThan(12)

  const loadMore = panel.getByRole('button', { name: /^Load \d+ more/ })
  if (await loadMore.isVisible()) {
    const scroll = page.getByTestId('job-list-scroll')
    await scroll.evaluate((el) => (el.scrollTop = el.scrollHeight))
    const before = await scroll.evaluate((el) => el.scrollTop)
    await loadMore.click()
    await expect(
      panel.getByRole('button', { name: /^Load \d+ more|^Loading/ }).or(panel.getByText(/jobs? loaded/)),
    ).toBeVisible()
    expect(await scroll.evaluate((el) => el.scrollTop)).toBe(before)
  }

  const shortlist = detail.getByRole('button', { name: /^Shortlist/ })
  test.skip(!(await shortlist.isVisible()), 'the job cannot be shortlisted')
  await shortlist.click()
  await expect(detail.getByText('Shortlisted').first()).toBeVisible()

  const note = `smoke ${Date.now()}`
  await detail.getByRole('textbox', { name: 'Notes' }).fill(note)
  await expect(detail.getByText('Saved · just now')).toBeVisible({ timeout: 5000 })

  await page.reload()
  const reloaded = page.getByRole('article').filter({ has: page.getByRole('heading', { level: 1 }) })
  await expect(reloaded.getByRole('textbox', { name: 'Notes' })).toHaveValue(note)
  await expect(reloaded.getByText('Shortlisted').first()).toBeVisible()
  await expect(reloaded.getByText('New → Shortlisted')).toBeVisible()
})

test('flow 4: mark a job Not eligible; Undo restores it; marked again it leaves the default view after a reload', async ({
  page,
}) => {
  const list = await grid(page)
  const card = list.getByRole('article').first()
  const path = await jobPath(card)
  await card.getByRole('link').first().click()
  const decision = page.getByRole('group', { name: 'Your decision' })
  await expect(decision).toBeVisible()
  test.skip(
    (await decision.getByRole('button', { name: /^Undecided/ }).getAttribute('aria-pressed')) !== 'true',
    'the first job already has a decision',
  )

  await decision.getByRole('button', { name: /^Not eligible/ }).click()
  await expect(decision.getByRole('button', { name: /^Not eligible/ })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: 'Undo' }).click()
  await expect(decision.getByRole('button', { name: /^Undecided/ })).toHaveAttribute('aria-pressed', 'true')

  await decision.getByRole('button', { name: /^Not eligible/ }).click()
  await expect(page.getByText('Marked not eligible')).toBeVisible()
  await page.goto('/')
  await expect(page.getByRole('list', { name: 'Jobs' }).or(page.getByText('Inbox zero'))).toBeVisible()
  await expect(page.locator(`a[href^="${path}?"], a[href="${path}"]`)).toHaveCount(0)

  // Clean up: back to undecided, so the job returns to the default view.
  await page.goto(`${path}?decision=INELIGIBLE`)
  await page
    .getByRole('group', { name: 'Your decision' })
    .getByRole('button', { name: /^Undecided/ })
    .click()
  await expect(page.getByText('Decision cleared')).toBeVisible()
  await page.goto('/')
  await expect(page.getByRole('list', { name: 'Jobs' })).toBeVisible()
})
