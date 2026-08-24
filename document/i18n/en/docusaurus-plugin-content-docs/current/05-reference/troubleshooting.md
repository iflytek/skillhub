---
title: Troubleshooting
sidebar_position: 2
description: Common problem diagnosis and solutions
---

# Troubleshooting

## Service Cannot Start

### Checklist

1. Check container status: `docker compose ps`
2. View service logs: `docker compose logs <service>`
3. Verify environment variables: Check `.env.release` configuration
4. Check port occupancy: `netstat -tlnp`

### Common Causes

- Port occupied
- Database connection failed
- Redis connection failed
- Environment variables missing

### PostgreSQL container fails to start with `operation not permitted` (cannot write `postmaster.pid` / `pg_wal`)

SkillHub's default Compose / `runtime.sh` deployment uses a Docker named volume (`postgres_data`), so you normally do not need to manage host directory permissions manually. This error is more common after changing PostgreSQL storage to a host bind mount, for example `/data/skillhub/postgres:/var/lib/postgresql/data`.

Recommended checks:

1. Prefer switching back to a Docker named volume, or use the official `runtime.sh` deployment script to avoid permission gaps from hand-written compose files.
2. If you must use a bind mount, first check the `postgres` UID/GID in the image you run: `docker run --rm postgres:16-alpine id postgres`. Then change the data directory owner to the actual UID/GID, for example `chown -R <uid>:<gid> <data-dir>`. Do not assume every environment is `999:999`.
3. On RHEL/CentOS, check SELinux. If AppArmor, rootless Docker, NFS/CIFS/NAS, or another restricted filesystem is involved, also verify that PostgreSQL can write, lock files, and change permissions as required.
4. Avoid putting PostgreSQL `PGDATA` on network filesystems that do not provide full POSIX permission semantics. For production, prefer local disks, Docker named volumes, block storage, or an external PostgreSQL service.

## Upload Failed

### Skill Package Upload Failed

1. Check file size
2. Check file type
3. Check SKILL.md format
4. View server logs

## Authentication Issues

### Cannot Login

1. Check OAuth configuration
2. Check callback URL configuration
3. Check `SKILLHUB_PUBLIC_BASE_URL` configuration

## Performance Issues

### Slow Search

1. Check PostgreSQL full-text index
2. Consider upgrading to Elasticsearch (future version)

### Slow Download

1. Check object storage configuration
2. Check network bandwidth

## Get Help

If above solutions cannot resolve the issue:
1. View logs
2. Submit Issue
3. Contact technical support

## Next Steps

- [Changelog](./changelog) - Version history
