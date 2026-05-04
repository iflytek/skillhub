# 本地运行速查表

本文只保留当前正式支持的源码启动组合，面向日常开发和联调。

更完整的运行时参数说明见：

- [../design/runtime/runtime-core-configuration-reference.md](../design/runtime/runtime-core-configuration-reference.md)

## 当前正式源码组合

当前正式源码运行 profile 仅保留 `local-mysql`。

### 1. MySQL + local-file-index + 本地缓存

适用场景：

- 想验证接近当前主运行时的数据与搜索行为
- 不想依赖 Redis
- 需要本地 mock UASS 登录页

前置依赖：

```bash
docker compose up -d mysql
```

后端：

```bash
SKILLHUB_SEARCH_PROVIDER=local-file-index \
SKILLHUB_RUNTIME_STATE_PROVIDER=memory \
SPRING_PROFILES_ACTIVE=local-mysql \
SKILLHUB_AUTH_UASS_ENABLED=true \
SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001 \
java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar
```

前端主站：

```bash
pnpm --dir web dev --host 127.0.0.1 --port 3000 --strictPort
```

mock 第三方登录页：

```bash
pnpm --dir web dev --host 127.0.0.1 --port 3001 --strictPort
```

访问地址：

- Web: `http://127.0.0.1:3000`
- Mock UASS: `http://127.0.0.1:3001/mock-uass`
- API: `http://127.0.0.1:8080`

### 2. MySQL + mysql-like + 本地缓存

适用场景：

- 需要验证 `mysql-like` 回退搜索链路
- 排查 Lucene 本地索引问题

前置依赖：

```bash
docker compose up -d mysql
```

后端：

```bash
SKILLHUB_SEARCH_PROVIDER=mysql-like \
SKILLHUB_RUNTIME_STATE_PROVIDER=memory \
SPRING_PROFILES_ACTIVE=local-mysql \
SKILLHUB_AUTH_UASS_ENABLED=true \
SKILLHUB_AUTH_UASS_MOCK_LOGIN_BASE_URL=http://localhost:3001 \
java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar
```

前端仍使用与上面相同的 `3000/3001` 启动方式。

## 登录方式

### 本地账号

- 用户名：`admin`
- 密码：`ChangeMe!2026`

### Mock UASS

如果启用了本地 UASS mock：

1. 打开登录页
2. 点击企业登录
3. 跳转到 `3001`
4. 在 mock 页里填写 `ussId`

当前预置全权限管理员：

- `uass-admin-003`

说明：

- `uass-admin-003` 首次通过 UASS 创建账号时会直接成为 `SUPER_ADMIN`
- 如果该账号已经存在，再改配置不会 retroactive 补权

## 常见检查项

### 后端健康检查

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

### 登录方式列表

```bash
curl -fsS 'http://127.0.0.1:8080/api/v1/auth/methods?returnTo=%2Fdashboard'
```

### 检查 provider 是否符合预期

看后端启动日志：

- `The following 1 profile is active: "local-mysql"`
- `UASS login state store is running in LOCAL mode`
- `Database: jdbc:mysql://localhost:3306/skillhub`

### local-file-index 空或损坏时

当前已经支持启动同步：

- Lucene 索引目录缺失/未初始化：自动 `rebuildAll()`
- Lucene 索引损坏：自动 `rebuildAll()`

如果想强制每次启动全量重建：

```bash
SKILLHUB_SEARCH_REBUILD_ON_STARTUP=true
```
