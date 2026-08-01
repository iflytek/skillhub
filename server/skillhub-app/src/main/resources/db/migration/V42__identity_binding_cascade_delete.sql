-- identity_binding.user_id references user_account(id) without ON DELETE CASCADE. When an
-- account is removed, its bindings would otherwise survive and block the identity from being
-- provisioned again. Cascade the deletion so bindings never outlive their account.
ALTER TABLE identity_binding DROP CONSTRAINT identity_binding_user_id_fkey;
ALTER TABLE identity_binding
    ADD CONSTRAINT identity_binding_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE;
