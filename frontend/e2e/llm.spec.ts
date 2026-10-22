import { expect, test } from '@playwright/test'

/**
 * T-023 smoke flow: a daily-quota 429 on the extraction model shows the banner on every page;
 * switching from the banner removes it after the next status poll.
 *
 * Needs a stack whose EXTRACT model is a fake one that answers with a quota 429
 * (`app.llm.fake.simulate=quota-after:0`, model assigned — not forced with `app.llm.force-model`,
 * which would override any switch) and a second usable model. Skips otherwise. No real LLM is called.
 */
test('quota 429 → banner on every page → switch model → banner gone', async ({ page }) => {
  await page.goto('/?analysisStage=NOT_ANALYZED')
  const list = page.getByRole('list', { name: 'Jobs' })
  const empty = page.getByText('Inbox zero')
  await expect(list.or(empty)).toBeVisible()
  test.skip(await empty.isVisible(), 'no job waits for Analyze')

  await list.getByRole('article').first().getByRole('link').first().click()
  const analyze = page.getByRole('button', { name: 'Analyze' }).first()
  test.skip(!(await analyze.isVisible()), 'the job cannot be analyzed')
  await analyze.click()

  const banner = page.getByRole('region', { name: 'LLM alerts' })
  const paused = banner.getByText('Extraction paused')
  await expect(paused.or(page.getByText(/Analysis failed|Analyzing/).first())).toBeVisible({ timeout: 15_000 })
  test.skip(!(await paused.isVisible()), 'the extraction model did not answer with a quota 429 (see the header)')

  await page.goto('/admin')
  await expect(banner.getByText('Extraction paused')).toBeVisible()
  await page.goto('/profile')
  await expect(banner.getByText('Extraction paused')).toBeVisible()

  await banner.getByRole('button', { name: 'Switch model' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Model for Extraction' })
  const choice = dialog.locator('input[type="radio"]:not(:checked):not(:disabled)').first()
  test.skip((await choice.count()) === 0, 'no other usable model')
  await choice.check()
  const confirm = dialog.getByRole('checkbox', { name: /I confirm paid calls/ })
  if (await confirm.isVisible()) await confirm.click()
  await dialog.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByText(/^Extraction now uses /)).toBeVisible()
  await expect(banner.getByText('Extraction paused')).toHaveCount(0, { timeout: 15_000 })
})
