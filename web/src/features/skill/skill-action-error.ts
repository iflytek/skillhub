export function resolveSkillActionErrorTitle(action: 'download' | 'report') {
  return action === 'download'
    ? 'skillDetail.downloadErrorTitle'
    : 'skillDetail.reportErrorTitle'
}
