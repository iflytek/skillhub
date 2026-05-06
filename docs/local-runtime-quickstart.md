# 本地运行速查表

本文只保留当前正式支持的源码启动组合，面向日常开发和联调。

更完整的运行时参数说明见：

- [../design/runtime/runtime-core-configuration-reference.md](../design/runtime/runtime-core-configuration-reference.md)

## 当前运行 profile

- `dev`: `MySQL + memory + mysql-like`
- `test`: `MySQL + Redis + mysql-like`
- `prod`: `MySQL + Redis + local-file-index`
- `qa`: `H2 + memory + local-file-index`，仅用于 `src/test` 下的自动化测试

### 1. `dev`: MySQL + memory + mysql-like

适用场景：

- 本地开发快速迭代
- 不想依赖 Redis

前置依赖：

```bash
docker compose up -d mysql
```

后端：

```bash
SPRING_PROFILES_ACTIVE=dev \
java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar
```

前端主站：

```bash
pnpm --dir web dev --host 127.0.0.1 --port 3000 --strictPort
```

访问地址：

- Web: `http://127.0.0.1:3000`
- API: `http://127.0.0.1:8080`

### 2. `test`: MySQL + Redis + mysql-like

适用场景：

- 联调接近非本地内存态的运行路径
- 需要验证 Redis 运行时状态链路

前置依赖：

```bash
docker compose up -d mysql redis
```

后端：

```bash
SPRING_PROFILES_ACTIVE=test \
java -jar server/skillhub-app/target/skillhub-app-0.1.0.jar
```

前端仍使用与上面相同的 `3000` 启动方式。

## 登录方式

### 本地账号

- 用户名：`admin`
- 密码：`ChangeMe!2026`

## 常见检查项

### 后端健康检查

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

### 登录方式列表

```bash
curl -fsS 'http://127.0.0.1:8080/api/v1/auth/methods?returnTo=%2Fdashboard'
```

### 检查 profile 是否符合预期

看后端启动日志：

- `The following 1 profile is active: "dev"` 或 `test`
- `Database: jdbc:mysql://localhost:3306/skillhub`
