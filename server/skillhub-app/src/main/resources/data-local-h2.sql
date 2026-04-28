MERGE INTO role (code, name, description, is_system, created_at)
KEY (code)
VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限', TRUE, CURRENT_TIMESTAMP()),
('SKILL_ADMIN', '技能管理员', '全局空间审核、提升审核、隐藏/撤回', TRUE, CURRENT_TIMESTAMP()),
('USER_ADMIN', '用户管理员', '准入审批、封禁/解封、角色分配', TRUE, CURRENT_TIMESTAMP()),
('AUDITOR', '审计员', '查看审计日志', TRUE, CURRENT_TIMESTAMP());

MERGE INTO namespace (slug, display_name, type, description, status, created_at, updated_at)
KEY (slug)
VALUES
('global', 'Global', 'GLOBAL', 'Platform-level public namespace', 'ACTIVE', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO user_account (id, display_name, email, avatar_url, status, merged_to_user_id, created_at, updated_at)
KEY (id)
VALUES
('local-user', 'Local Developer', 'local-user@example.test', NULL, 'ACTIVE', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('local-admin', 'Local Admin', 'local-admin@example.test', NULL, 'ACTIVE', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO local_credential (user_id, username, password_hash, failed_attempts, locked_until, created_at, updated_at)
KEY (user_id)
VALUES
('local-user', 'local-user', '$2a$12$IZVCL0BQ2WT/ijVgjTOP4eTjwSts15K7BWxLmm/FCtPGe0rFdQTve', 0, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('local-admin', 'admin', '$2a$12$IZVCL0BQ2WT/ijVgjTOP4eTjwSts15K7BWxLmm/FCtPGe0rFdQTve', 0, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO namespace_member (namespace_id, user_id, role, created_at, updated_at)
KEY (namespace_id, user_id)
SELECT n.id, 'local-user', 'OWNER', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
FROM namespace n
WHERE n.slug = 'global';

MERGE INTO namespace_member (namespace_id, user_id, role, created_at, updated_at)
KEY (namespace_id, user_id)
SELECT n.id, 'local-admin', 'OWNER', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
FROM namespace n
WHERE n.slug = 'global';

MERGE INTO user_role_binding (user_id, role_id, created_at)
KEY (user_id, role_id)
SELECT 'local-admin', r.id, CURRENT_TIMESTAMP()
FROM role r
WHERE r.code = 'SUPER_ADMIN';
