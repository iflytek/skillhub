export type Visibility = 'PRIVATE' | 'NAMESPACE_ONLY' | 'PUBLIC';
export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export interface AccountIdentity { handle: string; displayName: string }
export interface AccountNamespace {
  id: number; slug: string; displayName: string; type: string; status: string; currentUserRole: TeamRole;
}
export interface OwnedSkill {
  id: number; slug: string; namespace: string; displayName: string; visibility: Visibility; status: string;
  publishedVersion?: { id: number; version: string } | null;
}
export interface AccountMember { userId: string; displayName: string | null; role: TeamRole }
export interface MemberCandidate { userId: string; displayName: string | null; email: string | null }
export interface AccountPage<T> { items: T[]; page: number; size: number; total: number }
export interface WorkspaceSkills {
  items: { namespace: string; slug: string; version: string; versionId: number }[]; nextCursor: string | null;
}
export interface UploadTarget { slug: string; existing: { id: number; visibility: Visibility } | null }
export interface UploadReceipt { namespace: string; slug: string; version: string; visibility: Visibility }
export type EditableRole = Exclude<TeamRole, 'OWNER'>;
export type MemberMutation =
  | { action: 'add' | 'role'; userId: string; role: EditableRole }
  | { action: 'remove'; userId: string };
