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
