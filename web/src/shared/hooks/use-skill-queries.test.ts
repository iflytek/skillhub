import { afterEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n/config'
import {
  getAdminLabelDefinitionsQueryKey,
  getSkillDetailQueryKey,
  getSkillLabelsQueryKey,
  getVisibleLabelsQueryKey,
} from './query-keys'

describe('localized label query keys', () => {
  const originalLanguage = i18n.language
  const originalResolvedLanguage = i18n.resolvedLanguage

  afterEach(() => {
    i18n.language = originalLanguage
    i18n.resolvedLanguage = originalResolvedLanguage
  })

  it('includes the current language so localized label data refetches after language switches', () => {
    i18n.language = 'en'
    i18n.resolvedLanguage = 'en'

    expect(getVisibleLabelsQueryKey()).toEqual(['labels', 'visible', 'en'])
    expect(getSkillLabelsQueryKey('team', 'demo')).toEqual(['labels', 'skill', 'team', 'demo', 'en'])
    expect(getSkillDetailQueryKey('team', 'demo')).toEqual(['skills', 'team', 'demo', 'en'])
    expect(getAdminLabelDefinitionsQueryKey()).toEqual(['labels', 'admin', 'en'])

    i18n.language = 'zh-CN'
    i18n.resolvedLanguage = 'zh-CN'

    expect(getVisibleLabelsQueryKey()).toEqual(['labels', 'visible', 'zh-CN'])
    expect(getSkillLabelsQueryKey('team', 'demo')).toEqual(['labels', 'skill', 'team', 'demo', 'zh-CN'])
    expect(getSkillDetailQueryKey('team', 'demo')).toEqual(['skills', 'team', 'demo', 'zh-CN'])
    expect(getAdminLabelDefinitionsQueryKey()).toEqual(['labels', 'admin', 'zh-CN'])
  })
})
