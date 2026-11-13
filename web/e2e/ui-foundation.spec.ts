import { expect, test } from '@playwright/test'

const fixture = '/e2e/fixtures/ui-foundation.html'

for (const mode of ['none', 'empty', 'missing']) {
  test(`tabs provide entry without selecting when value is ${mode}`, async ({ page }) => {
    await page.goto(`${fixture}?mode=${mode}`)
    await expect(page.getByRole('tab', { selected: true })).toHaveCount(0)
    await expect(page.getByTestId('changes')).toHaveText('0')
    await expect(page.locator('[role="tab"][tabindex="0"]')).toHaveCount(1)
    await page.locator('#before').focus()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('tab', { name: 'First' })).toBeFocused()
    await expect(page.getByTestId('changes')).toHaveText('0')
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('tab', { name: 'Second' })).toBeFocused()
    await expect(page.getByRole('tabpanel')).toHaveText('second content')
    await expect(page.getByTestId('changes')).toHaveText('1')
  })
}

for (const action of ['Disable second', 'Remove second']) {
  test(`tabs recover keyboard entry after ${action}`, async ({ page }) => {
    await page.goto(`${fixture}?mode=selected`)
    await expect(page.getByRole('tab', { name: 'Second' })).toHaveAttribute('tabindex', '0')
    await page.getByRole('button', { name: action, exact: true }).click()
    await expect(page.getByTestId('changes')).toHaveText('0')
    await expect(page.locator('[role="tab"][tabindex="0"]')).toHaveCount(1)
    await page.locator('#before').focus()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('tab', { name: 'First' })).toBeFocused()
    await expect(page.getByTestId('changes')).toHaveText('0')
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('tab', { name: 'Third' })).toBeFocused()
  })
}

for (const dir of ['ltr', 'rtl']) {
  test(`tabs wrap and skip disabled triggers in ${dir}`, async ({ page }) => {
    await page.goto(`${fixture}?mode=disabled&dir=${dir}`)
    await page.locator('#before').focus()
    await page.keyboard.press('Tab')
    await page.keyboard.press(dir === 'rtl' ? 'ArrowLeft' : 'ArrowRight')
    await expect(page.getByRole('tab', { name: 'Third' })).toBeFocused()
    await page.keyboard.press(dir === 'rtl' ? 'ArrowLeft' : 'ArrowRight')
    await expect(page.getByRole('tab', { name: 'First' })).toBeFocused()
    await page.keyboard.press('End')
    await expect(page.getByRole('tab', { name: 'Third' })).toBeFocused()
    await page.keyboard.press('Home')
    await expect(page.getByRole('tab', { name: 'First' })).toBeFocused()
    await page.keyboard.press(dir === 'rtl' ? 'ArrowRight' : 'ArrowLeft')
    await expect(page.getByRole('tab', { name: 'Third' })).toBeFocused()
  })
}

test('all-disabled tabs let Tab leave the list', async ({ page }) => {
  await page.goto(`${fixture}?mode=all-disabled`)
  await expect(page.locator('[role="tab"][tabindex="0"]')).toHaveCount(0)
  await page.locator('#before').focus()
  await page.keyboard.press('Tab')
  await expect(page.locator('#after')).toBeFocused()
  await expect(page.getByTestId('changes')).toHaveText('0')
})

for (const density of ['regular', 'compact']) {
  for (const theme of ['light', 'dark']) {
    test(`controls retain density, overrides and visible states: ${theme}/${density}`, async ({ page }) => {
      await page.goto(`${fixture}?density=${density}&theme=${theme}`)
      const padding = density === 'compact' ? '8px' : '12px'
      for (const property of ['padding-left', 'padding-right']) {
        await expect(page.getByRole('columnheader')).toHaveCSS(property, padding)
        await expect(page.getByRole('cell')).toHaveCSS(property, padding)
      }
      await expect(page.getByTestId('default-control')).toHaveCSS('height', density === 'compact' ? '28px' : '32px')
      await expect(page.getByLabel('Name', { exact: true })).toHaveCSS('height', density === 'compact' ? '28px' : '32px')
      await expect(page.getByTestId('class-override')).toHaveCSS('height', '48px')
      await expect(page.getByLabel('Style override')).toHaveCSS('height', '52px')
      await expect(page.getByTestId('card')).toHaveCSS('box-shadow', /(?:none|rgba\(0, 0, 0, 0\))/)
      const normal = page.getByLabel('Name', { exact: true })
      const invalid = page.getByLabel('Version')
      expect(await invalid.evaluate(el => getComputedStyle(el).borderColor)).not.toBe(await normal.evaluate(el => getComputedStyle(el).borderColor))
      await expect(invalid).toHaveAccessibleDescription('Enter a valid version.')
      await normal.focus()
      // The adopted Input contract uses a focus border, not an outer shadow.
      await expect(normal).toHaveCSS('border-color', theme === 'dark' ? 'rgb(230, 230, 230)' : 'rgb(23, 23, 23)')
      await expect(normal).toHaveCSS('box-shadow', 'none')
      await expect(normal).toHaveCSS('outline-style', 'none')
      await page.emulateMedia({ reducedMotion: 'reduce' })
      const duration = await page.getByTestId('default-control').evaluate(el => getComputedStyle(el).transitionDuration)
      expect(duration.split(',').every(value => parseFloat(value) <= 0.001)).toBe(true)
    })
  }
}
