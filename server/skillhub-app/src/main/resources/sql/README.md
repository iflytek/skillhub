# SQL Layout

## Current default runtime path

- `migration-mysql/V1__init_local_mysql_schema.sql`

The standard runtime boots against MySQL and loads Flyway migrations from
`classpath:sql/migration-mysql`.
