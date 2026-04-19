import { test } from '@playwright/test'
import { prepareSearchSeed, writeSearchSeedDataset } from '../helpers/search-seed'

test.describe.configure({ mode: 'serial' })
test.setTimeout(300_000)

test('prepare heavy search seed dataset', async ({ browser }, testInfo) => {
  const seed = await prepareSearchSeed(browser, testInfo, {
    count: 13,
    persistSeed: true,
  })

  try {
    writeSearchSeedDataset({
      keyword: seed.keyword,
      namespace: seed.namespace,
      skills: seed.skills,
      skillNames: seed.skillNames,
    })
  } finally {
    await seed.dispose()
  }
})
