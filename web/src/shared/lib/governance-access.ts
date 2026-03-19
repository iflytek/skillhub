export function canViewGovernanceCenter(platformRoles?: readonly string[]) {
  if (!platformRoles?.length) {
    return false
  }

  return platformRoles.includes('SKILL_ADMIN')
    || platformRoles.includes('NAMESPACE_ADMIN')
    || platformRoles.includes('SUPER_ADMIN')
}
