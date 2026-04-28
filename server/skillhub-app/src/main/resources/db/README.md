# Database SQL Layout

## Active Flyway migration entry

- `migration/V1__init_schema.sql`

This is the consolidated initialization script used by Flyway for a fresh schema.

## Archived historical migrations

- `migration-archive/`

These files are preserved for history and troubleshooting, but are no longer scanned by Flyway.

## Destructive reset helper

- `reset/reset_postgres_schema.sql`

Use this only when you intentionally want to wipe an existing PostgreSQL schema and let Flyway
recreate it from the consolidated initialization script.

Typical flow:

1. Run `reset/reset_postgres_schema.sql` against the target PostgreSQL database.
2. Start the application with Flyway enabled.
3. Flyway applies `migration/V1__init_schema.sql` to rebuild the schema from scratch.

This helper is intentionally outside `db/migration` so it is never executed automatically.

