# SQL Layout

## Current default runtime path

- `migration-mysql/V1__init_local_mysql_schema.sql`

The standard runtime now boots against MySQL and loads Flyway migrations from
`classpath:sql/migration-mysql`.

## H2 lightweight local path

- `data-local-h2.sql`

`local-h2` keeps Flyway disabled and uses H2-specific bootstrap data for
lightweight development and test flows.

## Historical PostgreSQL archive

- `migration/V1__init_schema.sql`
- `migration/V2__add_uss_id_to_user_account.sql`
- `archive/reset_postgres_schema.sql`

These files are retained only for historical reference and one-off legacy data
cleanup. They are not part of the current standard MySQL runtime path.

Typical legacy reset flow:

1. Run `archive/reset_postgres_schema.sql` against the target PostgreSQL database.
2. Start the application with the legacy PostgreSQL Flyway path explicitly selected.
3. Flyway applies the archived `sql/migration/*.sql` chain.
