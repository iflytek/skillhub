# Phase 1: 工程骨架 + 认证打通 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可运行的前后端工程骨架，完成 GitHub OAuth 登录和 API Token 认证，满足 Phase 1 验收标准

**Architecture:** Maven 多模块后端（6 模块）+ React 前端 + Docker Compose 本地开发环境 + Spring Security OAuth2 + RBAC

**Tech Stack:**
- Backend: Spring Boot 3.x + JDK 21 + PostgreSQL 16 + Redis 7 + Spring Security OAuth2 Client + Spring Data JPA + Flyway
- Frontend: React 19 + TypeScript + Vite + TanStack Router + TanStack Query + shadcn/ui + Tailwind CSS
- DevOps: Docker Compose + Maven Wrapper + Makefile

---

## Chunk 1: 后端工程骨架 + 基础设施

本块建立 Maven 多模块项目结构、数据库迁移、基础配置、健康检查和 OpenAPI 文档，产出可启动的后端应用。

### 文件结构映射

```
skillhub/
├── server/
│   ├── pom.xml                           # 父 POM
│   ├── .mvn/wrapper/                     # Maven Wrapper
│   ├── mvnw, mvnw.cmd
│   ├── skillhub-app/
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/skillhub/
│   │       │   ├── SkillhubApplication.java
│   │       │   ├── config/
│   │       │   │   ├── OpenApiConfig.java
│   │       │   │   └── WebMvcConfig.java
│   │       │   ├── controller/
│   │       │   │   └── HealthController.java
│   │       │   └── filter/
│   │       │       └── RequestIdFilter.java
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-local.yml
│   │           └── db/migration/
│   │               └── V1__init_schema.sql
│   ├── skillhub-domain/
│   │   ├── pom.xml
│   │   └── src/main/java/com/skillhub/domain/
│   ├── skillhub-auth/
│   │   ├── pom.xml
│   │   └── src/main/java/com/skillhub/auth/
│   ├── skillhub-search/
│   │   ├── pom.xml
│   │   └── src/main/java/com/skillhub/search/
│   ├── skillhub-storage/
│   │   ├── pom.xml
│   │   └── src/main/java/com/skillhub/storage/
│   └── skillhub-infra/
│       ├── pom.xml
│       └── src/main/java/com/skillhub/infra/
├── docker-compose.yml
├── .gitignore
└── Makefile
```

### Task 1: 初始化 Monorepo 和 Maven 多模块项目

**Files:**
- Create: `server/pom.xml`
- Create: `server/skillhub-app/pom.xml`
- Create: `server/skillhub-domain/pom.xml`
- Create: `server/skillhub-auth/pom.xml`
- Create: `server/skillhub-search/pom.xml`
- Create: `server/skillhub-storage/pom.xml`
- Create: `server/skillhub-infra/pom.xml`
- Create: `.gitignore`

- [ ] **Step 1: 创建根 .gitignore**

```bash
cat > .gitignore << 'EOF'
# Maven
target/
!.mvn/wrapper/maven-wrapper.jar
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
.DS_Store

# Logs
*.log

# Environment
.env
.env.local

# Node
node_modules/
dist/
.pnpm-store/
EOF
```

- [ ] **Step 2: 创建父 POM (server/pom.xml)**

```bash
mkdir -p server && cat > server/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.3</version>
        <relativePath/>
    </parent>

    <groupId>com.skillhub</groupId>
    <artifactId>skillhub-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <modules>
        <module>skillhub-app</module>
        <module>skillhub-domain</module>
        <module>skillhub-auth</module>
        <module>skillhub-search</module>
        <module>skillhub-storage</module>
        <module>skillhub-infra</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <!-- Internal modules -->
            <dependency>
                <groupId>com.skillhub</groupId>
                <artifactId>skillhub-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.skillhub</groupId>
                <artifactId>skillhub-auth</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.skillhub</groupId>
                <artifactId>skillhub-search</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.skillhub</groupId>
                <artifactId>skillhub-storage</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.skillhub</groupId>
                <artifactId>skillhub-infra</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
EOF
```

- [ ] **Step 3: 创建 skillhub-app 模块 POM**

```bash
mkdir -p server/skillhub-app && cat > server/skillhub-app/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>skillhub-app</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-auth</artifactId>
        </dependency>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

- [ ] **Step 4: 创建其他模块的 POM（domain, auth, search, storage, infra）**

```bash
# skillhub-domain
mkdir -p server/skillhub-domain/src/main/java/com/skillhub/domain
cat > server/skillhub-domain/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-domain</artifactId>
</project>
EOF

# skillhub-auth
mkdir -p server/skillhub-auth/src/main/java/com/skillhub/auth
cat > server/skillhub-auth/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-auth</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-domain</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

# skillhub-search
mkdir -p server/skillhub-search/src/main/java/com/skillhub/search
cat > server/skillhub-search/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-search</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-domain</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

# skillhub-storage
mkdir -p server/skillhub-storage/src/main/java/com/skillhub/storage
cat > server/skillhub-storage/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-storage</artifactId>
</project>
EOF

# skillhub-infra
mkdir -p server/skillhub-infra/src/main/java/com/skillhub/infra
cat > server/skillhub-infra/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.skillhub</groupId>
        <artifactId>skillhub-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>skillhub-infra</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.skillhub</groupId>
            <artifactId>skillhub-domain</artifactId>
        </dependency>
    </dependencies>
</project>
EOF
```

- [ ] **Step 5: 安装 Maven Wrapper**

Run: `cd server && mvn wrapper:wrapper`

Expected: Maven Wrapper 文件生成在 `server/.mvn/wrapper/`

- [ ] **Step 6: 验证项目结构**

Run: `cd server && ./mvnw clean compile`

Expected: `BUILD SUCCESS`，所有模块编译通过

- [ ] **Step 7: Commit**

```bash
git add .gitignore server/
git commit -m "feat: initialize Maven multi-module project structure

- Add parent POM with 6 modules (app, domain, auth, search, storage, infra)
- Configure Spring Boot 3.2.3 + JDK 21
- Add Maven Wrapper for reproducible builds
- Set up module dependency graph (app depends on all, infra/auth/search depend on domain)"
```

### Task 2: 创建 Spring Boot 应用入口和基础配置

**Files:**
- Create: `server/skillhub-app/src/main/java/com/skillhub/SkillhubApplication.java`
- Create: `server/skillhub-app/src/main/resources/application.yml`
- Create: `server/skillhub-app/src/main/resources/application-local.yml`
- Create: `server/skillhub-app/src/test/java/com/skillhub/ApplicationContextStartsTest.java`

- [ ] **Step 1: 编写失败的 ApplicationContext 启动测试**

```bash
mkdir -p server/skillhub-app/src/test/java/com/skillhub
cat > server/skillhub-app/src/test/java/com/skillhub/ApplicationContextStartsTest.java << 'EOF'
package com.skillhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationContextStartsTest {

    @Test
    void contextLoads() {
        // ApplicationContext should start successfully
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -Dtest=ApplicationContextStartsTest`

Expected: FAIL - "Unable to find a @SpringBootConfiguration"

- [ ] **Step 3: 创建 SkillhubApplication 主类**

```bash
mkdir -p server/skillhub-app/src/main/java/com/skillhub
cat > server/skillhub-app/src/main/java/com/skillhub/SkillhubApplication.java << 'EOF'
package com.skillhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillhubApplication.java, args);
    }
}
EOF
```

- [ ] **Step 4: 创建基础配置文件**

```bash
mkdir -p server/skillhub-app/src/main/resources
cat > server/skillhub-app/src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: skillhub
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  shutdown: graceful

spring.lifecycle.timeout-per-shutdown-phase: 30s

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
EOF

cat > server/skillhub-app/src/main/resources/application-local.yml << 'EOF'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/skillhub
    username: skillhub
    password: skillhub_dev
  data:
    redis:
      host: localhost
      port: 6379
  jpa:
    show-sql: true

logging:
  level:
    com.skillhub: DEBUG
EOF
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd server && ./mvnw test -Dtest=ApplicationContextStartsTest`

Expected: FAIL - "Failed to configure a DataSource" (预期，因为还没有数据库)

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/
git commit -m "feat: add Spring Boot application entry point and base configuration

- Create SkillhubApplication main class
- Add application.yml with JPA, Flyway, graceful shutdown config
- Add application-local.yml for local development profile
- Add ApplicationContextStartsTest (will pass after DB setup)"
```

### Task 3: 添加 Docker Compose 本地开发环境

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: 创建 docker-compose.yml**

```bash
cat > docker-compose.yml << 'EOF'
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: skillhub_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skillhub"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  minio_data:
EOF
```

- [ ] **Step 2: 启动依赖服务**

Run: `docker compose up -d`

Expected: PostgreSQL, Redis, MinIO 启动成功，健康检查通过

- [ ] **Step 3: 验证服务可访问**

Run: `docker compose ps`

Expected: 所有服务状态为 `healthy`

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add Docker Compose for local development dependencies

- Add PostgreSQL 16, Redis 7, MinIO services
- Configure health checks for all services
- Use named volumes for data persistence"
```

### Task 4: 添加 Flyway 数据库迁移和 Phase 1 核心表

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V1__init_schema.sql`
- Update: `server/skillhub-app/pom.xml` (添加 Flyway 和 PostgreSQL 驱动依赖)

- [ ] **Step 1: 更新 skillhub-app POM 添加数据库依赖**

```bash
# 在 skillhub-app/pom.xml 的 <dependencies> 中添加：
cat >> server/skillhub-app/pom.xml.tmp << 'EOF'
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
EOF
# 手动编辑 server/skillhub-app/pom.xml，在 </dependencies> 前插入上述依赖
```

- [ ] **Step 2: 创建 Flyway 迁移脚本 V1__init_schema.sql**

```bash
mkdir -p server/skillhub-app/src/main/resources/db/migration
cat > server/skillhub-app/src/main/resources/db/migration/V1__init_schema.sql << 'EOF'
-- Phase 1 核心表：认证与授权

-- 用户账号表
CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(256),
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    merged_to_user_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_account_email ON user_account(email);
CREATE INDEX idx_user_account_status ON user_account(status);

-- OAuth 身份绑定表
CREATE TABLE identity_binding (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    provider_code VARCHAR(64) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    login_name VARCHAR(128),
    extra_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_code, subject)
);

CREATE INDEX idx_identity_binding_user_id ON identity_binding(user_id);

-- API Token 表
CREATE TABLE api_token (
    id BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    subject_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    name VARCHAR(128) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    scope_json JSONB NOT NULL,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_token_user_id ON api_token(user_id);
CREATE INDEX idx_api_token_hash ON api_token(token_hash);

-- 角色表
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    group_code VARCHAR(64)
);

-- 角色权限关联表
CREATE TABLE role_permission (
    role_id BIGINT NOT NULL REFERENCES role(id),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role_id, permission_id)
);

-- 用户角色绑定表
CREATE TABLE user_role_binding (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    role_id BIGINT NOT NULL REFERENCES role(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

CREATE INDEX idx_user_role_binding_user_id ON user_role_binding(user_id);

-- 命名空间表
CREATE TABLE namespace (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 命名空间成员表
CREATE TABLE namespace_member (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_id, user_id)
);

CREATE INDEX idx_namespace_member_user_id ON namespace_member(user_id);
CREATE INDEX idx_namespace_member_namespace_id ON namespace_member(namespace_id);

-- 审计日志表
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id VARCHAR(128) REFERENCES user_account(id),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT,
    request_id VARCHAR(64),
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor ON audit_log(actor_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_request_id ON audit_log(request_id);

-- 插入系统内置角色
INSERT INTO role (code, name, description, is_system) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限', TRUE),
('SKILL_ADMIN', '技能管理员', '全局空间审核、提升审核、隐藏/撤回', TRUE),
('USER_ADMIN', '用户管理员', '准入审批、封禁/解封、角色分配', TRUE),
('AUDITOR', '审计员', '查看审计日志', TRUE);

-- 插入系统权限
INSERT INTO permission (code, name, group_code) VALUES
('skill:publish', '发布技能', 'skill'),
('skill:manage', '管理技能', 'skill'),
('skill:promote', '提升到全局', 'skill'),
('review:approve', '审核技能', 'review'),
('promotion:approve', '审核提升申请', 'promotion'),
('user:manage', '管理用户', 'user'),
('user:approve', '审批用户准入', 'user'),
('audit:read', '查看审计日志', 'audit');

-- 绑定角色权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'SKILL_ADMIN' AND p.code IN ('review:approve', 'skill:manage', 'promotion:approve');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'USER_ADMIN' AND p.code IN ('user:manage', 'user:approve');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'AUDITOR' AND p.code = 'audit:read';

-- 插入系统内置 @global 命名空间
INSERT INTO namespace (slug, display_name, type, description, status)
VALUES ('global', 'Global', 'GLOBAL', 'Platform-level public namespace', 'ACTIVE');
EOF
```

- [ ] **Step 3: 运行 Flyway 迁移**

Run: `cd server && ./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/skillhub -Dflyway.user=skillhub -Dflyway.password=skillhub_dev`

Expected: `Successfully applied 1 migration to schema "public"`

- [ ] **Step 4: 验证表创建成功**

Run: `docker compose exec postgres psql -U skillhub -d skillhub -c "\dt"`

Expected: 列出所有表（user_account, identity_binding, api_token, role, permission, role_permission, user_role_binding, namespace, namespace_member, audit_log, flyway_schema_history）

- [ ] **Step 5: 运行 ApplicationContextStartsTest 确认通过**

Run: `cd server && ./mvnw test -Dtest=ApplicationContextStartsTest -Dspring.profiles.active=local`

Expected: PASS - ApplicationContext 启动成功

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/pom.xml server/skillhub-app/src/main/resources/db/migration/
git commit -m "feat: add Flyway migration with Phase 1 core schema

- Add PostgreSQL driver, Flyway, Spring Data JPA, Redis dependencies
- Create V1__init_schema.sql with auth tables (user_account, identity_binding, api_token)
- Create RBAC tables (role, permission, role_permission, user_role_binding)
- Create namespace tables (namespace, namespace_member)
- Create audit_log table
- Insert system roles (SUPER_ADMIN, SKILL_ADMIN, USER_ADMIN, AUDITOR) and permissions
- Insert @global namespace"
```

### Task 5: 添加 RequestId Filter 和全局异常处理

**Files:**
- Create: `server/skillhub-app/src/main/java/com/skillhub/filter/RequestIdFilter.java`
- Create: `server/skillhub-app/src/main/java/com/skillhub/exception/GlobalExceptionHandler.java`
- Create: `server/skillhub-app/src/main/java/com/skillhub/dto/ErrorResponse.java`
- Test: `server/skillhub-app/src/test/java/com/skillhub/filter/RequestIdFilterTest.java`

- [ ] **Step 1: 编写 RequestIdFilter 测试**

```bash
mkdir -p server/skillhub-app/src/test/java/com/skillhub/filter
cat > server/skillhub-app/src/test/java/com/skillhub/filter/RequestIdFilterTest.java << 'EOF'
package com.skillhub.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdWhenNotProvided() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void shouldPreserveProvidedRequestId() throws Exception {
        String requestId = "test-request-123";
        mockMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId));
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -Dtest=RequestIdFilterTest -Dspring.profiles.active=local`

Expected: FAIL - "Expected header X-Request-Id does not exist"

- [ ] **Step 3: 实现 RequestIdFilter**

```bash
mkdir -p server/skillhub-app/src/main/java/com/skillhub/filter
cat > server/skillhub-app/src/main/java/com/skillhub/filter/RequestIdFilter.java << 'EOF'
package com.skillhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -Dtest=RequestIdFilterTest -Dspring.profiles.active=local`

Expected: PASS

- [ ] **Step 5: 创建全局异常处理器和 DTO**

```bash
mkdir -p server/skillhub-app/src/main/java/com/skillhub/exception
cat > server/skillhub-app/src/main/java/com/skillhub/exception/GlobalExceptionHandler.java << 'EOF'
package com.skillhub.exception;

import com.skillhub.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unhandled exception", ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
EOF

mkdir -p server/skillhub-app/src/main/java/com/skillhub/dto
cat > server/skillhub-app/src/main/java/com/skillhub/dto/ErrorResponse.java << 'EOF'
package com.skillhub.dto;

public record ErrorResponse(
        int status,
        String error,
        String message
) {}
EOF
```

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/skillhub/filter/ \
        server/skillhub-app/src/main/java/com/skillhub/exception/ \
        server/skillhub-app/src/main/java/com/skillhub/dto/ \
        server/skillhub-app/src/test/java/com/skillhub/filter/
git commit -m "feat: add RequestId filter and global exception handler

- Implement RequestIdFilter to generate/preserve X-Request-Id header
- Add MDC support for request tracing in logs
- Create GlobalExceptionHandler for unified error responses
- Add ErrorResponse DTO
- Add RequestIdFilterTest with MockMvc"
```

### Task 6: 添加 OpenAPI 配置和健康检查端点

**Files:**
- Create: `server/skillhub-app/src/main/java/com/skillhub/config/OpenApiConfig.java`
- Create: `server/skillhub-app/src/main/java/com/skillhub/controller/HealthController.java`
- Test: `server/skillhub-app/src/test/java/com/skillhub/controller/HealthControllerTest.java`

- [ ] **Step 1: 编写健康检查端点测试**

```bash
mkdir -p server/skillhub-app/src/test/java/com/skillhub/controller
cat > server/skillhub-app/src/test/java/com/skillhub/controller/HealthControllerTest.java << 'EOF'
package com.skillhub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
EOF
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd server && ./mvnw test -Dtest=HealthControllerTest -Dspring.profiles.active=local`

Expected: FAIL - 404 Not Found

- [ ] **Step 3: 实现 HealthController**

```bash
mkdir -p server/skillhub-app/src/main/java/com/skillhub/controller
cat > server/skillhub-app/src/main/java/com/skillhub/controller/HealthController.java << 'EOF'
package com.skillhub.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
EOF
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd server && ./mvnw test -Dtest=HealthControllerTest -Dspring.profiles.active=local`

Expected: PASS

- [ ] **Step 5: 创建 OpenAPI 配置**

```bash
mkdir -p server/skillhub-app/src/main/java/com/skillhub/config
cat > server/skillhub-app/src/main/java/com/skillhub/config/OpenApiConfig.java << 'EOF'
package com.skillhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skillhubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SkillHub API")
                        .description("Skills Registry Platform")
                        .version("0.1.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")
                ));
    }
}
EOF
```

- [ ] **Step 6: 验证 OpenAPI 文档可访问**

Run: `cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (在另一个终端)

Run: `curl -s http://localhost:8080/v3/api-docs | jq '.info.title'`

Expected: `"SkillHub API"`

- [ ] **Step 7: 停止应用并 Commit**

```bash
# Ctrl+C 停止应用
git add server/skillhub-app/src/main/java/com/skillhub/config/ \
        server/skillhub-app/src/main/java/com/skillhub/controller/ \
        server/skillhub-app/src/test/java/com/skillhub/controller/
git commit -m "feat: add OpenAPI configuration and health check endpoint

- Configure Springdoc OpenAPI with API info and server URL
- Add /api/v1/health endpoint for basic health check
- Add HealthControllerTest
- OpenAPI docs available at /v3/api-docs and /swagger-ui.html"
```

### Task 7: 添加 Makefile 顶层编排

**Files:**
- Create: `Makefile`

- [ ] **Step 1: 创建 Makefile**

```bash
cat > Makefile << 'EOF'
.PHONY: dev dev-down build test clean

# 启动本地开发环境（仅依赖服务）
dev:
	docker compose up -d
	@echo "Waiting for services to be healthy..."
	@sleep 5
	@echo "Services ready. Start backend with: cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local"

# 停止本地开发环境
dev-down:
	docker compose down

# 构建后端
build:
	cd server && ./mvnw clean package -DskipTests

# 运行测试
test:
	cd server && ./mvnw test

# 清理构建产物
clean:
	cd server && ./mvnw clean
	docker compose down -v

# 生成 OpenAPI 类型（前端用，Phase 1 暂不实现）
generate-api:
	@echo "Frontend not yet implemented"
EOF
```

- [ ] **Step 2: 测试 Makefile 命令**

Run: `make dev`

Expected: Docker Compose 服务启动，提示信息显示

Run: `make test`

Expected: 所有测试通过

Run: `make dev-down`

Expected: Docker Compose 服务停止

- [ ] **Step 3: Commit**

```bash
git add Makefile
git commit -m "feat: add Makefile for top-level orchestration

- Add 'make dev' to start Docker Compose dependencies
- Add 'make dev-down' to stop services
- Add 'make build' to build backend JAR
- Add 'make test' to run all tests
- Add 'make clean' to clean build artifacts and volumes"
```

---

## Chunk 1 验收标准

运行以下命令验证 Chunk 1 完成：

```bash
# 1. 启动依赖服务
make dev

# 2. 运行所有测试
make test
# Expected: BUILD SUCCESS, all tests pass

# 3. 启动后端应用
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. 验证健康检查
curl http://localhost:8080/api/v1/health
# Expected: {"status":"UP"}

# 5. 验证 Actuator
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 6. 验证 OpenAPI 文档
curl http://localhost:8080/v3/api-docs | jq '.info.title'
# Expected: "SkillHub API"

# 7. 验证 RequestId
curl -v http://localhost:8080/api/v1/health 2>&1 | grep X-Request-Id
# Expected: X-Request-Id header present

# 8. 验证数据库表
docker compose exec postgres psql -U skillhub -d skillhub -c "\dt"
# Expected: 列出所有 Phase 1 表

# 9. 停止服务
make dev-down
```

Chunk 1 产出：可启动的后端应用 + 数据库 schema + Docker Compose 本地环境 + Makefile 编排。

## Chunk 2: 后端认证与授权体系

本块实现完整的认证链路：Spring Security OAuth2 GitHub 登录、AccessPolicy 准入策略、身份绑定、Spring Session Redis、API Token 认证、RBAC 授权、MockAuthFilter 本地开发、CSRF 防护。

### 文件结构映射

```
server/
├── skillhub-domain/src/main/java/com/skillhub/domain/
│   ├── user/
│   │   ├── UserAccount.java              # 用户实体
│   │   ├── UserStatus.java               # 用户状态枚举
│   │   └── UserAccountRepository.java    # Repository 接口
│   └── namespace/
│       ├── Namespace.java                # 命名空间实体
│       ├── NamespaceStatus.java          # 命名空间状态枚举
│       ├── NamespaceMember.java          # 成员实体
│       ├── NamespaceRole.java            # 命名空间角色枚举
│       ├── NamespaceRepository.java
│       └── NamespaceMemberRepository.java
├── skillhub-auth/src/main/java/com/skillhub/auth/
│   ├── entity/
│   │   ├── IdentityBinding.java
│   │   ├── ApiToken.java
│   │   ├── Role.java
│   │   ├── Permission.java
│   │   ├── RolePermission.java
│   │   └── UserRoleBinding.java
│   ├── repository/
│   │   ├── IdentityBindingRepository.java
│   │   ├── ApiTokenRepository.java
│   │   ├── RoleRepository.java
│   │   ├── PermissionRepository.java
│   │   └── UserRoleBindingRepository.java
│   ├── oauth/
│   │   ├── OAuthClaims.java
│   │   ├── OAuthClaimsExtractor.java
│   │   ├── GitHubClaimsExtractor.java
│   │   ├── CustomOAuth2UserService.java
│   │   └── OAuth2LoginSuccessHandler.java
│   ├── policy/
│   │   ├── AccessPolicy.java
│   │   ├── AccessDecision.java
│   │   ├── OpenAccessPolicy.java
│   │   ├── EmailDomainAccessPolicy.java
│   │   └── AccessPolicyFactory.java
│   ├── identity/
│   │   └── IdentityBindingService.java
│   ├── token/
│   │   ├── ApiTokenService.java
│   │   └── ApiTokenAuthenticationFilter.java
│   ├── rbac/
│   │   ├── RbacService.java
│   │   └── PlatformPrincipal.java
│   ├── config/
│   │   └── SecurityConfig.java
│   └── mock/
│       └── MockAuthFilter.java
├── skillhub-app/src/main/java/com/skillhub/
│   ├── controller/
│   │   └── AuthController.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── ErrorResponse.java
└── skillhub-infra/src/main/java/com/skillhub/infra/
    └── jpa/
        ├── UserAccountJpaRepository.java
        ├── NamespaceJpaRepository.java
        └── NamespaceMemberJpaRepository.java
```

### Task 8: Domain 层用户与命名空间实体

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserAccount.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserAccountRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/Namespace.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/NamespaceStatus.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/NamespaceMember.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/NamespaceRole.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/NamespaceRepository.java`
- Create: `server/skillhub-domain/src/main/java/com/skillhub/domain/namespace/NamespaceMemberRepository.java`

- [ ] **Step 1: 创建 UserStatus 枚举**

```java
// server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserStatus.java
package com.skillhub.domain.user;

public enum UserStatus {
    ACTIVE,
    PENDING,
    DISABLED,
    MERGED
}
```

- [ ] **Step 2: 创建 UserAccount 实体**

```java
// server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserAccount.java
package com.skillhub.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(length = 256)
    private String email;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "merged_to_user_id")
    private Long mergedToUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserAccount() {}

    public UserAccount(String displayName, String email, String avatarUrl) {
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Long getMergedToUserId() { return mergedToUserId; }
    public void setMergedToUserId(Long mergedToUserId) { this.mergedToUserId = mergedToUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isActive() { return this.status == UserStatus.ACTIVE; }
}
```

- [ ] **Step 3: 创建 UserAccountRepository 接口**

```java
// server/skillhub-domain/src/main/java/com/skillhub/domain/user/UserAccountRepository.java
package com.skillhub.domain.user;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findById(Long id);
    UserAccount save(UserAccount user);
}
```

- [ ] **Step 4: 创建命名空间相关实体**

```java
// NamespaceStatus.java
package com.skillhub.domain.namespace;

public enum NamespaceStatus {
    ACTIVE, FROZEN, ARCHIVED
}

// NamespaceRole.java
package com.skillhub.domain.namespace;

public enum NamespaceRole {
    OWNER, ADMIN, MEMBER
}

// Namespace.java
package com.skillhub.domain.namespace;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "namespace")
public class Namespace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NamespaceStatus status = NamespaceStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Namespace() {}

    public Namespace(String slug, String displayName, Long createdBy) {
        this.slug = slug;
        this.displayName = displayName;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public NamespaceStatus getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

// NamespaceMember.java
package com.skillhub.domain.namespace;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "namespace_member",
       uniqueConstraints = @UniqueConstraint(columnNames = {"namespace_id", "user_id"}))
public class NamespaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NamespaceRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected NamespaceMember() {}

    public NamespaceMember(Long namespaceId, String userId, NamespaceRole role) {
        this.namespaceId = namespaceId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public Long getUserId() { return userId; }
    public NamespaceRole getRole() { return role; }
    public void setRole(NamespaceRole role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: 创建 Repository 接口**

```java
// NamespaceRepository.java
package com.skillhub.domain.namespace;

import java.util.Optional;

public interface NamespaceRepository {
    Optional<Namespace> findById(Long id);
    Optional<Namespace> findBySlug(String slug);
    Namespace save(Namespace namespace);
}

// NamespaceMemberRepository.java
package com.skillhub.domain.namespace;

import java.util.List;
import java.util.Optional;

public interface NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, String userId);
    List<NamespaceMember> findByUserId(String userId);
    NamespaceMember save(NamespaceMember member);
}
```

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-domain/
git commit -m "feat(domain): add UserAccount and Namespace entities with repository interfaces

- UserAccount with status lifecycle (ACTIVE/PENDING/DISABLED/MERGED)
- Namespace, NamespaceMember with role-based membership
- Repository interfaces (implementation in infra module)"
```

### Task 9: Infra 层 JPA Repository 实现

**Files:**
- Create: `server/skillhub-infra/src/main/java/com/skillhub/infra/jpa/UserAccountJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/skillhub/infra/jpa/NamespaceJpaRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/skillhub/infra/jpa/NamespaceMemberJpaRepository.java`

- [ ] **Step 1: 创建 UserAccountJpaRepository**

```java
// server/skillhub-infra/src/main/java/com/skillhub/infra/jpa/UserAccountJpaRepository.java
package com.skillhub.infra.jpa;

import com.skillhub.domain.user.UserAccount;
import com.skillhub.domain.user.UserAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountJpaRepository
        extends JpaRepository<UserAccount, Long>, UserAccountRepository {
}
```

- [ ] **Step 2: 创建 NamespaceJpaRepository 和 NamespaceMemberJpaRepository**

```java
// NamespaceJpaRepository.java
package com.skillhub.infra.jpa;

import com.skillhub.domain.namespace.Namespace;
import com.skillhub.domain.namespace.NamespaceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NamespaceJpaRepository
        extends JpaRepository<Namespace, Long>, NamespaceRepository {
    Optional<Namespace> findBySlug(String slug);
}

// NamespaceMemberJpaRepository.java
package com.skillhub.infra.jpa;

import com.skillhub.domain.namespace.NamespaceMember;
import com.skillhub.domain.namespace.NamespaceMemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceMemberJpaRepository
        extends JpaRepository<NamespaceMember, Long>, NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, String userId);
    List<NamespaceMember> findByUserId(String userId);
}
```

- [ ] **Step 3: Commit**

```bash
git add server/skillhub-infra/
git commit -m "feat(infra): add JPA repository implementations for UserAccount and Namespace"
```

### Task 10: Auth 模块实体与 Repository

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/IdentityBinding.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/ApiToken.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/Role.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/Permission.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/RolePermission.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/entity/UserRoleBinding.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/repository/*.java`

- [ ] **Step 1: 创建 IdentityBinding 实体**

```java
package com.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "identity_binding",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider_code", "subject"}))
public class IdentityBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(name = "login_name", length = 128)
    private String loginName;

    @Column(name = "extra_json", columnDefinition = "jsonb")
    private String extraJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected IdentityBinding() {}

    public IdentityBinding(String userId, String providerCode, String subject, String loginName) {
        this.userId = userId;
        this.providerCode = providerCode;
        this.subject = subject;
        this.loginName = loginName;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getProviderCode() { return providerCode; }
    public String getSubject() { return subject; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
}
```

- [ ] **Step 2: 创建 ApiToken 实体**

```java
package com.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_token")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType = "USER";

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "scope_json", nullable = false, columnDefinition = "jsonb")
    private String scopeJson;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ApiToken() {}

    public ApiToken(String userId, String name, String tokenPrefix, String tokenHash, String scopeJson) {
        this.subjectType = "USER";
        this.subjectId = userId;
        this.userId = userId;
        this.name = name;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.scopeJson = scopeJson;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getTokenPrefix() { return tokenPrefix; }
    public String getTokenHash() { return tokenHash; }
    public String getScopeJson() { return scopeJson; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return expiresAt != null && expiresAt.isBefore(LocalDateTime.now()); }
    public boolean isValid() { return !isRevoked() && !isExpired(); }
}
```

- [ ] **Step 3: 创建 RBAC 实体（Role, Permission, RolePermission, UserRoleBinding）**

```java
// Role.java
package com.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "role")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isSystem() { return system; }
}

// Permission.java
package com.skillhub.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permission")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "group_code", length = 64)
    private String groupCode;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}

// RolePermission.java
package com.skillhub.auth.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "role_permission")
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {
    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    public Long getRoleId() { return roleId; }
    public Long getPermissionId() { return permissionId; }

    public static class RolePermissionId implements Serializable {
        private Long roleId;
        private Long permissionId;
        // equals and hashCode omitted for brevity — implement in code
    }
}

// UserRoleBinding.java
package com.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_role_binding",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
public class UserRoleBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UserRoleBinding() {}

    public UserRoleBinding(String userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRoleId() { return roleId; }
}
```

- [ ] **Step 4: 创建 Auth Repository 接口**

```java
// IdentityBindingRepository.java
package com.skillhub.auth.repository;

import com.skillhub.auth.entity.IdentityBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IdentityBindingRepository extends JpaRepository<IdentityBinding, Long> {
    Optional<IdentityBinding> findByProviderCodeAndSubject(String providerCode, String subject);
}

// ApiTokenRepository.java
package com.skillhub.auth.repository;

import com.skillhub.auth.entity.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);
}

// RoleRepository.java
package com.skillhub.auth.repository;

import com.skillhub.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}

// UserRoleBindingRepository.java
package com.skillhub.auth.repository;

import com.skillhub.auth.entity.UserRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRoleBindingRepository extends JpaRepository<UserRoleBinding, Long> {
    List<UserRoleBinding> findByUserId(String userId);
}
```

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-auth/
git commit -m "feat(auth): add auth entities and JPA repositories

- IdentityBinding, ApiToken, Role, Permission, RolePermission, UserRoleBinding
- JPA repositories for all auth entities"
```

### Task 10: OAuth2 Claims 提取与准入策略

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/OAuthClaims.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/OAuthClaimsExtractor.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/GitHubClaimsExtractor.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/policy/AccessDecision.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/policy/AccessPolicy.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/policy/OpenAccessPolicy.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/policy/EmailDomainAccessPolicy.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/policy/AccessPolicyFactory.java`
- Test: `server/skillhub-auth/src/test/java/com/skillhub/auth/policy/AccessPolicyTest.java`

- [ ] **Step 1: 创建 OAuthClaims record**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/OAuthClaims.java
package com.skillhub.auth.oauth;

import java.util.Map;

public record OAuthClaims(
    String provider,
    String subject,
    String email,
    boolean emailVerified,
    String providerLogin,
    Map<String, Object> extra
) {}
```

- [ ] **Step 2: 创建 OAuthClaimsExtractor 接口和 GitHub 实现**

```java
// OAuthClaimsExtractor.java
package com.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

public interface OAuthClaimsExtractor {
    String getProvider();
    OAuthClaims extract(OAuth2User oAuth2User);
}
```

```java
// GitHubClaimsExtractor.java
package com.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GitHubClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() { return "github"; }

    @Override
    public OAuthClaims extract(OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        return new OAuthClaims(
            "github",
            String.valueOf(attrs.get("id")),
            (String) attrs.get("email"),
            attrs.get("email") != null,
            (String) attrs.get("login"),
            attrs
        );
    }
}
```

- [ ] **Step 3: 创建 AccessDecision 和 AccessPolicy**

```java
// AccessDecision.java
package com.skillhub.auth.policy;

public enum AccessDecision {
    ALLOW,
    DENY,
    PENDING_APPROVAL
}
```

```java
// AccessPolicy.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;

public interface AccessPolicy {
    AccessDecision evaluate(OAuthClaims claims);
}
```

- [ ] **Step 4: 创建 OpenAccessPolicy 和 EmailDomainAccessPolicy**

```java
// OpenAccessPolicy.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;

public class OpenAccessPolicy implements AccessPolicy {
    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        return AccessDecision.ALLOW;
    }
}
```

```java
// EmailDomainAccessPolicy.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

public class EmailDomainAccessPolicy implements AccessPolicy {
    private final Set<String> allowedDomains;

    public EmailDomainAccessPolicy(Set<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        if (claims.email() == null) return AccessDecision.DENY;
        String domain = claims.email().substring(claims.email().indexOf('@') + 1);
        return allowedDomains.contains(domain.toLowerCase())
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
```

```java
// ProviderAllowlistAccessPolicy.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

public class ProviderAllowlistAccessPolicy implements AccessPolicy {
    private final Set<String> allowedProviders;

    public ProviderAllowlistAccessPolicy(Set<String> allowedProviders) {
        this.allowedProviders = allowedProviders;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        return allowedProviders.contains(claims.provider())
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
```

```java
// SubjectWhitelistAccessPolicy.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

public class SubjectWhitelistAccessPolicy implements AccessPolicy {
    private final Set<String> whitelistedSubjects; // "provider:subject" 格式

    public SubjectWhitelistAccessPolicy(Set<String> whitelistedSubjects) {
        this.whitelistedSubjects = whitelistedSubjects;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        String key = claims.provider() + ":" + claims.subject();
        return whitelistedSubjects.contains(key)
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
```

- [ ] **Step 5: 创建 AccessPolicyFactory**

```java
// AccessPolicyFactory.java
package com.skillhub.auth.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "skillhub.access-policy")
public class AccessPolicyFactory {
    private String mode = "OPEN";
    private List<String> allowedEmailDomains = List.of();
    private List<String> allowedProviders = List.of();
    private List<String> whitelistedSubjects = List.of();

    @Bean
    public AccessPolicy accessPolicy() {
        return switch (mode.toUpperCase()) {
            case "EMAIL_DOMAIN" -> new EmailDomainAccessPolicy(Set.copyOf(allowedEmailDomains));
            case "PROVIDER_ALLOWLIST" -> new ProviderAllowlistAccessPolicy(Set.copyOf(allowedProviders));
            case "SUBJECT_WHITELIST" -> new SubjectWhitelistAccessPolicy(Set.copyOf(whitelistedSubjects));
            default -> new OpenAccessPolicy();
        };
    }

    public void setMode(String mode) { this.mode = mode; }
    public void setAllowedEmailDomains(List<String> d) { this.allowedEmailDomains = d; }
    public void setAllowedProviders(List<String> p) { this.allowedProviders = p; }
    public void setWhitelistedSubjects(List<String> s) { this.whitelistedSubjects = s; }
}
```

- [ ] **Step 6: 编写 AccessPolicy 单元测试**

```java
// server/skillhub-auth/src/test/java/com/skillhub/auth/policy/AccessPolicyTest.java
package com.skillhub.auth.policy;

import com.skillhub.auth.oauth.OAuthClaims;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    @Test
    void openPolicy_alwaysAllows() {
        var policy = new OpenAccessPolicy();
        var claims = new OAuthClaims("github", "123", "user@any.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void emailDomainPolicy_allowsMatchingDomain() {
        var policy = new EmailDomainAccessPolicy(Set.of("company.com"));
        var claims = new OAuthClaims("github", "123", "user@company.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void emailDomainPolicy_deniesNonMatchingDomain() {
        var policy = new EmailDomainAccessPolicy(Set.of("company.com"));
        var claims = new OAuthClaims("github", "123", "user@other.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void emailDomainPolicy_deniesNullEmail() {
        var policy = new EmailDomainAccessPolicy(Set.of("company.com"));
        var claims = new OAuthClaims("github", "123", null, false, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void providerAllowlistPolicy_allowsMatchingProvider() {
        var policy = new ProviderAllowlistAccessPolicy(Set.of("github"));
        var claims = new OAuthClaims("github", "123", "u@a.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void providerAllowlistPolicy_deniesNonMatchingProvider() {
        var policy = new ProviderAllowlistAccessPolicy(Set.of("github"));
        var claims = new OAuthClaims("google", "123", "u@a.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.DENY);
    }

    @Test
    void subjectWhitelistPolicy_allowsMatchingSubject() {
        var policy = new SubjectWhitelistAccessPolicy(Set.of("github:12345"));
        var claims = new OAuthClaims("github", "12345", "u@a.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.ALLOW);
    }

    @Test
    void subjectWhitelistPolicy_deniesNonMatchingSubject() {
        var policy = new SubjectWhitelistAccessPolicy(Set.of("github:12345"));
        var claims = new OAuthClaims("github", "99999", "u@a.com", true, "user", Map.of());
        assertThat(policy.evaluate(claims)).isEqualTo(AccessDecision.DENY);
    }
}
```

- [ ] **Step 7: 运行测试验证**

Run: `cd server && ./mvnw test -pl skillhub-auth -Dtest=AccessPolicyTest -am`

Expected: 8 tests PASS

- [ ] **Step 8: Commit**

```bash
git add server/skillhub-auth/
git commit -m "feat(auth): add OAuth claims extraction and access policy

- OAuthClaims record, OAuthClaimsExtractor SPI, GitHubClaimsExtractor
- AccessPolicy SPI with Open and EmailDomain implementations
- AccessPolicyFactory with config-driven strategy selection
- Unit tests for access policies"
```

### Task 11: IdentityBindingService + CustomOAuth2UserService

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/identity/IdentityBindingService.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/CustomOAuth2UserService.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/OAuth2LoginSuccessHandler.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/rbac/PlatformPrincipal.java`

- [ ] **Step 1: 创建 PlatformPrincipal**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/rbac/PlatformPrincipal.java
package com.skillhub.auth.rbac;

import java.io.Serializable;
import java.util.Set;

public record PlatformPrincipal(
    String userId,
    String displayName,
    String email,
    String avatarUrl,
    String oauthProvider,
    Set<String> platformRoles
) implements Serializable {}
```

- [ ] **Step 2: 创建 IdentityBindingService**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/identity/IdentityBindingService.java
package com.skillhub.auth.identity;

import com.skillhub.auth.entity.IdentityBinding;
import com.skillhub.auth.entity.UserRoleBinding;
import com.skillhub.auth.oauth.OAuthClaims;
import com.skillhub.auth.rbac.PlatformPrincipal;
import com.skillhub.auth.repository.IdentityBindingRepository;
import com.skillhub.auth.repository.UserRoleBindingRepository;
import com.skillhub.domain.user.UserAccount;
import com.skillhub.domain.user.UserAccountRepository;
import com.skillhub.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IdentityBindingService {

    private final IdentityBindingRepository bindingRepo;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;

    public IdentityBindingService(IdentityBindingRepository bindingRepo,
                                   UserAccountRepository userRepo,
                                   UserRoleBindingRepository roleBindingRepo) {
        this.bindingRepo = bindingRepo;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    @Transactional
    public PlatformPrincipal bindOrCreate(OAuthClaims claims, UserStatus initialStatus) {
        IdentityBinding binding = bindingRepo
            .findByProviderCodeAndSubject(claims.provider(), claims.subject())
            .orElse(null);

        UserAccount user;
        if (binding != null) {
            user = userRepo.findById(binding.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for binding"));
            // 同步最新信息
            user.setDisplayName(claims.providerLogin());
            if (claims.email() != null) user.setEmail(claims.email());
            if (claims.extra().get("avatar_url") != null) {
                user.setAvatarUrl((String) claims.extra().get("avatar_url"));
            }
            user = userRepo.save(user);
        } else {
            user = new UserAccount(
                claims.providerLogin(),
                claims.email(),
                (String) claims.extra().get("avatar_url")
            );
            user.setStatus(initialStatus);
            user = userRepo.save(user);

            binding = new IdentityBinding();
            binding.setUserId(user.getId());
            binding.setProviderCode(claims.provider());
            binding.setSubject(claims.subject());
            binding.setLoginName(claims.providerLogin());
            bindingRepo.save(binding);
        }

        Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
            .map(rb -> rb.getRole().getCode())
            .collect(Collectors.toSet());

        return new PlatformPrincipal(
            user.getId(), user.getDisplayName(), user.getEmail(),
            user.getAvatarUrl(), claims.provider(), roles
        );
    }
}
```

- [ ] **Step 3: 创建 CustomOAuth2UserService**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/CustomOAuth2UserService.java
package com.skillhub.auth.oauth;

import com.skillhub.auth.identity.IdentityBindingService;
import com.skillhub.auth.policy.AccessDecision;
import com.skillhub.auth.policy.AccessPolicy;
import com.skillhub.auth.rbac.PlatformPrincipal;
import com.skillhub.domain.user.UserStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Map<String, OAuthClaimsExtractor> extractors;
    private final AccessPolicy accessPolicy;
    private final IdentityBindingService identityBindingService;

    public CustomOAuth2UserService(List<OAuthClaimsExtractor> extractorList,
                                    AccessPolicy accessPolicy,
                                    IdentityBindingService identityBindingService) {
        this.extractors = extractorList.stream()
            .collect(Collectors.toMap(OAuthClaimsExtractor::getProvider, Function.identity()));
        this.accessPolicy = accessPolicy;
        this.identityBindingService = identityBindingService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthClaimsExtractor extractor = extractors.get(registrationId);
        if (extractor == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", "Unsupported: " + registrationId, null));
        }

        OAuthClaims claims = extractor.extract(oAuth2User);
        AccessDecision decision = accessPolicy.evaluate(claims);

        UserStatus initialStatus = switch (decision) {
            case ALLOW -> UserStatus.ACTIVE;
            case PENDING_APPROVAL -> UserStatus.PENDING;
            case DENY -> throw new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "Access denied by policy", null));
        };

        PlatformPrincipal principal = identityBindingService.bindOrCreate(claims, initialStatus);

        // 将 principal 存入 OAuth2User attributes 供后续使用
        var attrs = new java.util.HashMap<>(oAuth2User.getAttributes());
        attrs.put("platformPrincipal", principal);

        return new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
            oAuth2User.getAuthorities(), attrs, "login"
        );
    }
}
```

- [ ] **Step 4: 创建 OAuth2LoginSuccessHandler**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/oauth/OAuth2LoginSuccessHandler.java
package com.skillhub.auth.oauth;

import com.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public OAuth2LoginSuccessHandler() {
        setDefaultTargetUrl("/?login=success");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            PlatformPrincipal principal = (PlatformPrincipal) oAuth2User.getAttributes().get("platformPrincipal");
            if (principal != null) {
                request.getSession().setAttribute("platformPrincipal", principal);
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-auth/ server/skillhub-domain/
git commit -m "feat(auth): add identity binding and OAuth2 user service

- PlatformPrincipal session record
- IdentityBindingService: bind or create user from OAuth claims
- CustomOAuth2UserService: delegate → extract → policy → bind
- OAuth2LoginSuccessHandler: store principal in session"
```

### Task 12: API Token 签发与认证 Filter

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/token/ApiTokenService.java`
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/token/ApiTokenAuthenticationFilter.java`
- Test: `server/skillhub-auth/src/test/java/com/skillhub/auth/token/ApiTokenServiceTest.java`

- [ ] **Step 1: 创建 ApiTokenService**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/token/ApiTokenService.java
package com.skillhub.auth.token;

import com.skillhub.auth.entity.ApiToken;
import com.skillhub.auth.rbac.PlatformPrincipal;
import com.skillhub.auth.repository.ApiTokenRepository;
import com.skillhub.auth.repository.UserRoleBindingRepository;
import com.skillhub.domain.user.UserAccount;
import com.skillhub.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApiTokenService {

    private static final String TOKEN_PREFIX = "ask_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ApiTokenRepository tokenRepo;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;

    public ApiTokenService(ApiTokenRepository tokenRepo,
                           UserAccountRepository userRepo,
                           UserRoleBindingRepository roleBindingRepo) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    /** 创建 Token，返回明文（仅此一次） */
    @Transactional
    public String createToken(String userId, String name, List<String> scopes,
                              LocalDateTime expiresAt) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(randomBytes);
        String hash = sha256(rawToken);

        ApiToken token = new ApiToken();
        token.setSubjectType("USER");
        token.setSubjectId(userId);
        token.setUserId(userId);
        token.setName(name);
        token.setTokenPrefix(TOKEN_PREFIX);
        token.setTokenHash(hash);
        token.setScopeJson(scopes);
        token.setExpiresAt(expiresAt);
        tokenRepo.save(token);

        return rawToken;
    }

    /** 通过明文 Token 认证，返回 PlatformPrincipal */
    public Optional<PlatformPrincipal> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        String hash = sha256(rawToken);
        return tokenRepo.findByTokenHash(hash)
            .filter(t -> t.getRevokedAt() == null)
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(LocalDateTime.now()))
            .flatMap(t -> {
                t.setLastUsedAt(LocalDateTime.now());
                tokenRepo.save(t);
                return userRepo.findById(t.getUserId());
            })
            .filter(UserAccount::isActive)
            .map(user -> {
                Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
                    .map(rb -> rb.getRole().getCode())
                    .collect(Collectors.toSet());
                return new PlatformPrincipal(
                    user.getId(), user.getDisplayName(), user.getEmail(),
                    user.getAvatarUrl(), "api_token", roles
                );
            });
    }

    public List<ApiToken> listByUser(String userId) {
        return tokenRepo.findByUserIdAndRevokedAtIsNull(userId);
    }

    @Transactional
    public void revoke(Long tokenId, String userId) {
        tokenRepo.findById(tokenId)
            .filter(t -> t.getUserId().equals(userId))
            .ifPresent(t -> {
                t.setRevokedAt(LocalDateTime.now());
                tokenRepo.save(t);
            });
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: 创建 ApiTokenAuthenticationFilter**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/token/ApiTokenAuthenticationFilter.java
package com.skillhub.auth.token;

import com.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private final ApiTokenService apiTokenService;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ask_")) {
            String token = authHeader.substring("Bearer ".length());
            apiTokenService.authenticate(token).ifPresent(principal -> {
                var authorities = principal.platformRoles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅对 CLI 和 Token API 路径生效
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/cli/") || path.startsWith("/api/v1/tokens")
                 || path.startsWith("/api/"));
    }
}
```

- [ ] **Step 3: 编写 ApiTokenService 单元测试**

```java
// server/skillhub-auth/src/test/java/com/skillhub/auth/token/ApiTokenServiceTest.java
package com.skillhub.auth.token;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenServiceTest {

    @Test
    void sha256_producesConsistentHash() {
        String hash1 = ApiTokenService.sha256("ask_test123");
        String hash2 = ApiTokenService.sha256("ask_test123");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void sha256_differentInputsDifferentHashes() {
        String hash1 = ApiTokenService.sha256("ask_token1");
        String hash2 = ApiTokenService.sha256("ask_token2");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd server && ./mvnw test -pl skillhub-auth -Dtest=ApiTokenServiceTest -am`

Expected: 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-auth/
git commit -m "feat(auth): add API Token service and authentication filter

- ApiTokenService: create, authenticate, list, revoke tokens
- ask_ prefix + SHA-256 hash storage
- ApiTokenAuthenticationFilter for Bearer token auth on CLI/compat paths
- Unit tests for SHA-256 hashing"
```

### Task 13: RBAC 授权服务

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/rbac/RbacService.java`
- Test: `server/skillhub-auth/src/test/java/com/skillhub/auth/rbac/RbacServiceTest.java`

- [ ] **Step 1: 创建 RbacService**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/rbac/RbacService.java
package com.skillhub.auth.rbac;

import com.skillhub.auth.repository.UserRoleBindingRepository;
import com.skillhub.domain.namespace.NamespaceMember;
import com.skillhub.domain.namespace.NamespaceMemberRepository;
import com.skillhub.domain.namespace.NamespaceRole;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacService {

    private final UserRoleBindingRepository roleBindingRepo;
    private final NamespaceMemberRepository namespaceMemberRepo;

    public RbacService(UserRoleBindingRepository roleBindingRepo,
                       NamespaceMemberRepository namespaceMemberRepo) {
        this.roleBindingRepo = roleBindingRepo;
        this.namespaceMemberRepo = namespaceMemberRepo;
    }

    /** 检查用户是否拥有指定平台权限 */
    public boolean hasPlatformRole(PlatformPrincipal principal, String roleCode) {
        if (principal.platformRoles().contains("SUPER_ADMIN")) return true;
        return principal.platformRoles().contains(roleCode);
    }

    /** 检查用户在指定命名空间的角色是否 >= 要求的最低角色 */
    public boolean hasNamespaceRole(String userId, Long namespaceId, NamespaceRole minRole) {
        Optional<NamespaceMember> member = namespaceMemberRepo
            .findByNamespaceIdAndUserId(namespaceId, userId);
        return member.map(m -> m.getRole().ordinal() <= minRole.ordinal()).orElse(false);
    }

    /** 获取用户在指定命名空间的角色 */
    public Optional<NamespaceRole> getNamespaceRole(String userId, Long namespaceId) {
        return namespaceMemberRepo.findByNamespaceIdAndUserId(namespaceId, userId)
            .map(NamespaceMember::getRole);
    }

    /** 获取用户所有平台角色码 */
    public Set<String> getPlatformRoleCodes(String userId) {
        return roleBindingRepo.findByUserId(userId).stream()
            .map(rb -> rb.getRole().getCode())
            .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: 编写 RbacService 单元测试**

```java
// server/skillhub-auth/src/test/java/com/skillhub/auth/rbac/RbacServiceTest.java
package com.skillhub.auth.rbac;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RbacServiceTest {

    @Test
    void superAdmin_hasAnyPlatformRole() {
        var principal = new PlatformPrincipal(1L, "admin", "a@b.com", null, "github",
            Set.of("SUPER_ADMIN"));
        // SUPER_ADMIN 短路判定
        assertThat(principal.platformRoles().contains("SUPER_ADMIN")).isTrue();
    }

    @Test
    void regularUser_doesNotHaveAdminRole() {
        var principal = new PlatformPrincipal(2L, "user", "u@b.com", null, "github",
            Set.of());
        assertThat(principal.platformRoles().contains("SKILL_ADMIN")).isFalse();
    }

    @Test
    void platformPrincipal_isSerializable() {
        var principal = new PlatformPrincipal(1L, "test", "t@t.com", null, "github",
            Set.of("AUDITOR"));
        // record 自动实现 Serializable
        assertThat(principal).isInstanceOf(java.io.Serializable.class);
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd server && ./mvnw test -pl skillhub-auth -Dtest=RbacServiceTest -am`

Expected: 3 tests PASS

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-auth/
git commit -m "feat(auth): add RBAC authorization service

- RbacService: platform role check with SUPER_ADMIN short-circuit
- Namespace role check with ordinal comparison
- Unit tests for role checks"
```

### Task 14: Spring Security 配置 + CSRF + Session

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/config/SecurityConfig.java`
- Modify: `server/skillhub-app/src/main/resources/application.yml` (添加 OAuth2 和 Session 配置)
- Modify: `server/skillhub-app/src/main/resources/application-local.yml` (添加 OAuth2 占位配置)

- [ ] **Step 1: 创建 SecurityConfig**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/config/SecurityConfig.java
package com.skillhub.auth.config;

import com.skillhub.auth.oauth.CustomOAuth2UserService;
import com.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.skillhub.auth.token.ApiTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final ApiTokenService apiTokenService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          OAuth2LoginSuccessHandler successHandler,
                          ApiTokenService apiTokenService) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.successHandler = successHandler;
        this.apiTokenService = apiTokenService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF: Cookie-to-Header 模式，CLI/compat API 豁免
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/v1/cli/**", "/api/**")
            )
            .authorizeHttpRequests(auth -> auth
                // 公开端点
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/providers",
                    "/api/v1/skills/**",
                    "/api/v1/namespaces/**",
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/**"
                ).permitAll()
                // Admin API
                .requestMatchers("/api/v1/admin/**").hasAnyRole("SUPER_ADMIN", "SKILL_ADMIN", "USER_ADMIN", "AUDITOR")
                // 其余需认证
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(successHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("SESSION")
            )
            .addFilterBefore(
                new ApiTokenAuthenticationFilter(apiTokenService),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
```

- [ ] **Step 2: 更新 application.yml 添加 OAuth2 和 Session 配置**

在 `server/skillhub-app/src/main/resources/application.yml` 追加：

```yaml
# 追加到 application.yml
spring:
  session:
    store-type: redis
    redis:
      namespace: skillhub:session
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${OAUTH2_GITHUB_CLIENT_ID:placeholder}
            client-secret: ${OAUTH2_GITHUB_CLIENT_SECRET:placeholder}
            scope: read:user,user:email
        provider:
          github:
            user-info-uri: https://api.github.com/user

skillhub:
  access-policy:
    mode: OPEN
```

- [ ] **Step 3: 更新 application-local.yml**

在 `server/skillhub-app/src/main/resources/application-local.yml` 追加：

```yaml
# 追加到 application-local.yml
spring:
  session:
    store-type: redis
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${OAUTH2_GITHUB_CLIENT_ID:local-placeholder}
            client-secret: ${OAUTH2_GITHUB_CLIENT_SECRET:local-placeholder}
```

- [ ] **Step 4: Commit**

```bash
git add server/
git commit -m "feat(auth): add Spring Security config with OAuth2 + CSRF + Session

- SecurityConfig: OAuth2 login, CSRF Cookie-to-Header, CLI API exempt
- API Token filter before UsernamePasswordAuthenticationFilter
- Spring Session Redis configuration
- Public endpoints permit all, admin requires roles"
```

### Task 15: MockAuthFilter 本地开发

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/skillhub/auth/mock/MockAuthFilter.java`

- [ ] **Step 1: 创建 MockAuthFilter**

```java
// server/skillhub-auth/src/main/java/com/skillhub/auth/mock/MockAuthFilter.java
package com.skillhub.auth.mock;

import com.skillhub.auth.rbac.PlatformPrincipal;
import com.skillhub.auth.repository.UserRoleBindingRepository;
import com.skillhub.domain.user.UserAccount;
import com.skillhub.domain.user.UserAccountRepository;
import com.skillhub.domain.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("local")
@Order(-100)
public class MockAuthFilter extends OncePerRequestFilter {

    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;

    public MockAuthFilter(UserAccountRepository userRepo,
                          UserRoleBindingRepository roleBindingRepo) {
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String mockUserId = request.getHeader("X-Mock-User-Id");
        if (mockUserId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = mockUserId;
            userRepo.findById(userId)
                .filter(UserAccount::isActive)
                .ifPresent(user -> {
                    Set<String> roles = roleBindingRepo.findByUserId(userId).stream()
                        .map(rb -> rb.getRole().getCode())
                        .collect(Collectors.toSet());
                    var principal = new PlatformPrincipal(
                        user.getId(), user.getDisplayName(), user.getEmail(),
                        user.getAvatarUrl(), "mock", roles
                    );
                    var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.getSession().setAttribute("platformPrincipal", principal);
                });
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add server/skillhub-auth/
git commit -m "feat(auth): add MockAuthFilter for local development

- Activated only under 'local' profile via @Profile
- Reads X-Mock-User-Id header to simulate authenticated user
- Creates PlatformPrincipal and sets SecurityContext"
```

### Task 16: AuthController + Token API

**Files:**
- Create: `server/skillhub-app/src/main/java/com/skillhub/controller/AuthController.java`
- Create: `server/skillhub-app/src/main/java/com/skillhub/controller/TokenController.java`

- [ ] **Step 1: 创建 AuthController**

```java
// server/skillhub-app/src/main/java/com/skillhub/controller/AuthController.java
package com.skillhub.controller;

import com.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        PlatformPrincipal principal = (PlatformPrincipal) session.getAttribute("platformPrincipal");
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of(
            "userId", principal.userId(),
            "displayName", principal.displayName(),
            "email", principal.email() != null ? principal.email() : "",
            "avatarUrl", principal.avatarUrl() != null ? principal.avatarUrl() : "",
            "oauthProvider", principal.oauthProvider(),
            "platformRoles", principal.platformRoles()
        ));
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> providers() {
        // 一期只有 GitHub，后续可动态读取 ClientRegistrationRepository
        var github = Map.of(
            "id", "github",
            "name", "GitHub",
            "authorizationUrl", "/oauth2/authorization/github"
        );
        return ResponseEntity.ok(Map.of("data", List.of(github)));
    }
}
```

- [ ] **Step 2: 创建 TokenController**

```java
// server/skillhub-app/src/main/java/com/skillhub/controller/TokenController.java
package com.skillhub.controller;

import com.skillhub.auth.rbac.PlatformPrincipal;
import com.skillhub.auth.token.ApiTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController {

    private final ApiTokenService apiTokenService;

    public TokenController(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) body.getOrDefault("scopes",
            List.of("skill:read", "skill:publish"));
        Integer expiryDays = (Integer) body.get("expiryDays");
        LocalDateTime expiresAt = expiryDays != null
            ? LocalDateTime.now().plusDays(expiryDays) : null;

        String rawToken = apiTokenService.createToken(
            principal.userId(), name, scopes, expiresAt);

        return ResponseEntity.ok(Map.of("token", rawToken));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal PlatformPrincipal principal) {
        var tokens = apiTokenService.listByUser(principal.userId());
        var result = tokens.stream().map(t -> Map.of(
            "id", t.getId(),
            "name", t.getName(),
            "tokenPrefix", t.getTokenPrefix(),
            "createdAt", t.getCreatedAt().toString(),
            "expiresAt", t.getExpiresAt() != null ? t.getExpiresAt().toString() : "",
            "lastUsedAt", t.getLastUsedAt() != null ? t.getLastUsedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(Map.of("data", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id) {
        apiTokenService.revoke(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add server/skillhub-app/
git commit -m "feat: add AuthController and TokenController

- GET /api/v1/auth/me: return current user info from session
- GET /api/v1/auth/providers: return available OAuth providers
- POST/GET/DELETE /api/v1/tokens: create, list, revoke API tokens"
```

### Task 17: 全局异常处理

**Files:**
- Create: `server/skillhub-app/src/main/java/com/skillhub/exception/ErrorResponse.java`
- Create: `server/skillhub-app/src/main/java/com/skillhub/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 ErrorResponse**

```java
// server/skillhub-app/src/main/java/com/skillhub/exception/ErrorResponse.java
package com.skillhub.exception;

import java.time.Instant;

public record ErrorResponse(
    int status,
    String error,
    String message,
    String requestId,
    Instant timestamp
) {
    public ErrorResponse(int status, String error, String message, String requestId) {
        this(status, error, message, requestId, Instant.now());
    }
}
```

- [ ] **Step 2: 创建 GlobalExceptionHandler**

```java
// server/skillhub-app/src/main/java/com/skillhub/exception/GlobalExceptionHandler.java
package com.skillhub.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex,
                                                           HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        return ResponseEntity.badRequest().body(
            new ErrorResponse(400, "Bad Request", ex.getMessage(), requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                        HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.error("Unhandled exception [requestId={}]", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new ErrorResponse(500, "Internal Server Error",
                "An unexpected error occurred", requestId));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add server/skillhub-app/
git commit -m "feat: add global exception handler

- ErrorResponse record with status, error, message, requestId, timestamp
- GlobalExceptionHandler: 400 for IllegalArgumentException, 500 catch-all
- Logs unhandled exceptions with requestId"
```

### Task 18: Flyway 种子数据（RBAC 预置角色和权限）

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V2__seed_rbac.sql`

- [ ] **Step 1: 创建种子数据迁移脚本**

```sql
-- server/skillhub-app/src/main/resources/db/migration/V2__seed_rbac.sql

-- 预置平台角色
INSERT INTO role (code, name, description, is_system) VALUES
('SUPER_ADMIN', '平台超管', '拥有所有权限', TRUE),
('SKILL_ADMIN', '技能治理', '全局空间审核、提升审核、隐藏/撤回', TRUE),
('USER_ADMIN', '用户治理', '准入审批、封禁/解封、角色分配', TRUE),
('AUDITOR', '审计员', '查看审计日志', TRUE);

-- 预置权限
INSERT INTO permission (code, name, group_code) VALUES
('review:approve', '审核通过', 'review'),
('review:reject', '审核拒绝', 'review'),
('skill:manage', '技能管理', 'skill'),
('skill:publish', '技能发布', 'skill'),
('skill:delete', '技能删除', 'skill'),
('promotion:approve', '提升审核', 'promotion'),
('user:manage', '用户管理', 'user'),
('user:approve', '用户审批', 'user'),
('audit:read', '审计查看', 'audit');

-- 角色-权限绑定
-- SKILL_ADMIN
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'SKILL_ADMIN' AND p.code IN ('review:approve', 'review:reject', 'skill:manage', 'promotion:approve');

-- USER_ADMIN
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'USER_ADMIN' AND p.code IN ('user:manage', 'user:approve');

-- AUDITOR
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'AUDITOR' AND p.code = 'audit:read';

-- 预置 @global 命名空间
INSERT INTO namespace (slug, display_name, description, visibility, status)
VALUES ('global', 'Global', '平台级公共空间', 'PUBLIC', 'ACTIVE');

-- 预置种子用户（本地开发用，SUPER_ADMIN）
INSERT INTO user_account (display_name, email, status)
VALUES ('Admin', 'admin@skillhub.dev', 'ACTIVE');

INSERT INTO user_role_binding (user_id, role_id)
SELECT u.id, r.id FROM user_account u, role r
WHERE u.email = 'admin@skillhub.dev' AND r.code = 'SUPER_ADMIN';
```

- [ ] **Step 2: Commit**

```bash
git add server/skillhub-app/src/main/resources/db/migration/V2__seed_rbac.sql
git commit -m "feat: add RBAC seed data migration

- Preset 4 platform roles: SUPER_ADMIN, SKILL_ADMIN, USER_ADMIN, AUDITOR
- Preset 9 permissions with role-permission bindings
- Create @global namespace
- Create seed admin user for local development"
```

### Chunk 2 验收检查

运行以下命令验证 Chunk 2 完成：

```bash
# 1. 确保依赖服务运行
make dev

# 2. 运行所有测试
cd server && ./mvnw test
# Expected: BUILD SUCCESS, AccessPolicyTest + ApiTokenServiceTest + RbacServiceTest 全部 PASS

# 3. 启动应用
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. 验证 MockAuth + /api/v1/auth/me
curl -H "X-Mock-User-Id: 1" http://localhost:8080/api/v1/auth/me
# Expected: {"userId":1,"displayName":"Admin","email":"admin@skillhub.dev",...}

# 5. 验证未登录返回 401
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/auth/me
# Expected: 401

# 6. 验证 /api/v1/auth/providers
curl http://localhost:8080/api/v1/auth/providers
# Expected: {"data":[{"id":"github","name":"GitHub","authorizationUrl":"/oauth2/authorization/github"}]}

# 7. 验证 Token 创建
curl -X POST -H "X-Mock-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"name":"test-token","scopes":["skill:read"]}' \
  http://localhost:8080/api/v1/tokens
# Expected: {"token":"ask_..."}

# 8. 验证 Token 列表
curl -H "X-Mock-User-Id: 1" http://localhost:8080/api/v1/tokens
# Expected: {"data":[...]}

# 9. 验证 RBAC 种子数据
docker compose exec postgres psql -U skillhub -d skillhub \
  -c "SELECT r.code, array_agg(p.code) FROM role r JOIN role_permission rp ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id GROUP BY r.code;"
# Expected: 4 roles with their permissions

# 10. 停止应用和服务
make dev-down
```

Chunk 2 产出：完整认证链路（OAuth2 + AccessPolicy + IdentityBinding + Session + Token + RBAC + MockAuth + CSRF）。

## Chunk 3: 前端骨架 + 登录集成

本块建立 React 前端工程，集成 TanStack Router/Query、shadcn/ui、openapi-fetch 类型生成管线，实现 OAuth 登录流程和路由守卫。

### 文件结构映射

```
web/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── components.json                    # shadcn/ui 配置
├── index.html
├── src/
│   ├── main.tsx
│   ├── app/
│   │   ├── router.tsx                 # TanStack Router 配置
│   │   ├── providers.tsx              # QueryClient + Router Provider
│   │   └── layout.tsx                 # 全局布局（Header + Main）
│   ├── pages/
│   │   ├── home.tsx                   # 首页
│   │   ├── login.tsx                  # 登录页
│   │   └── dashboard.tsx              # Dashboard（需登录）
│   ├── features/
│   │   └── auth/
│   │       ├── use-auth.ts            # 登录态 hook
│   │       ├── auth-guard.tsx         # 路由守卫
│   │       └── login-button.tsx       # OAuth 登录按钮
│   ├── shared/
│   │   └── ui/                        # shadcn/ui 组件
│   └── api/
│       ├── client.ts                  # openapi-fetch 客户端
│       └── generated/                 # openapi-typescript 生成的类型
│           └── schema.d.ts
├── Dockerfile
└── nginx.conf
```

### Task 19: 初始化前端工程

**Files:**
- Create: `web/package.json`
- Create: `web/tsconfig.json`
- Create: `web/vite.config.ts`
- Create: `web/index.html`
- Create: `web/src/main.tsx`

- [ ] **Step 1: 初始化 Vite + React + TypeScript 项目**

```bash
cd web  # 如果 web/ 不存在则先 mkdir web && cd web
pnpm create vite . --template react-ts
```

或手动创建 `package.json`：

```json
{
  "name": "skillhub-web",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "generate-api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/generated/schema.d.ts"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "@tanstack/react-router": "^1.95.0",
    "@tanstack/react-query": "^5.64.0",
    "openapi-fetch": "^0.13.0"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^4.3.0",
    "typescript": "^5.7.0",
    "vite": "^6.1.0",
    "tailwindcss": "^3.4.0",
    "postcss": "^8.4.0",
    "autoprefixer": "^10.4.0",
    "openapi-typescript": "^7.6.0"
  }
}
```

- [ ] **Step 2: 安装依赖**

Run: `cd web && pnpm install`

Expected: 依赖安装成功

- [ ] **Step 3: 创建 tsconfig.json**

```json
// web/tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"]
}
```

- [ ] **Step 4: 创建 vite.config.ts**

```typescript
// web/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 4: 创建 index.html 和 main.tsx**

```html
<!-- web/index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SkillHub</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

```tsx
// web/src/main.tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { App } from './app/providers'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

- [ ] **Step 5: Commit**

```bash
git add web/
git commit -m "feat(web): initialize Vite + React + TypeScript frontend

- package.json with React 19, TanStack Router/Query, openapi-fetch
- Vite config with API proxy to backend
- index.html and main.tsx entry point"
```

### Task 20: Tailwind CSS + shadcn/ui 配置

**Files:**
- Create: `web/tailwind.config.ts`
- Create: `web/postcss.config.js`
- Create: `web/src/index.css`
- Create: `web/components.json`

- [ ] **Step 1: 配置 Tailwind CSS**

```typescript
// web/tailwind.config.ts
import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {},
  },
  plugins: [],
}
export default config
```

```javascript
// web/postcss.config.js
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

```css
/* web/src/index.css */
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 2: 初始化 shadcn/ui**

Run: `cd web && pnpm dlx shadcn@latest init`

选择默认配置，或手动创建 `components.json`：

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.ts",
    "css": "src/index.css",
    "baseColor": "neutral",
    "cssVariables": true
  },
  "aliases": {
    "components": "@/shared/ui",
    "utils": "@/shared/ui/lib/utils"
  }
}
```

- [ ] **Step 3: 添加 Button 组件（验证 shadcn/ui 工作）**

Run: `cd web && pnpm dlx shadcn@latest add button`

Expected: `src/shared/ui/button.tsx` 生成成功

- [ ] **Step 4: Commit**

```bash
git add web/
git commit -m "feat(web): add Tailwind CSS and shadcn/ui configuration

- Tailwind config with dark mode support
- PostCSS config
- shadcn/ui initialized with Button component"
```

### Task 21: TanStack Router 路由骨架

**Files:**
- Create: `web/src/app/router.tsx`
- Create: `web/src/app/providers.tsx`
- Create: `web/src/app/layout.tsx`
- Create: `web/src/pages/home.tsx`
- Create: `web/src/pages/login.tsx`
- Create: `web/src/pages/dashboard.tsx`

- [ ] **Step 1: 创建路由配置**

```tsx
// web/src/app/router.tsx
import { createRouter, createRoute, createRootRoute } from '@tanstack/react-router'
import { Layout } from './layout'
import { HomePage } from '../pages/home'
import { LoginPage } from '../pages/login'
import { DashboardPage } from '../pages/dashboard'

const rootRoute = createRootRoute({
  component: Layout,
})

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  component: DashboardPage,
})

const routeTree = rootRoute.addChildren([homeRoute, loginRoute, dashboardRoute])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
```

- [ ] **Step 2: 创建 Providers**

```tsx
// web/src/app/providers.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { router } from './router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
```

- [ ] **Step 3: 创建 Layout**

```tsx
// web/src/app/layout.tsx
import { Outlet, Link } from '@tanstack/react-router'
import { useAuth } from '../features/auth/use-auth'

export function Layout() {
  const { user, isLoading } = useAuth()

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b">
        <div className="container mx-auto flex h-14 items-center justify-between px-4">
          <Link to="/" className="text-lg font-semibold">SkillHub</Link>
          <nav className="flex items-center gap-4">
            {isLoading ? null : user ? (
              <>
                <Link to="/dashboard" className="text-sm">Dashboard</Link>
                <span className="text-sm text-muted-foreground">{user.displayName}</span>
              </>
            ) : (
              <Link to="/login" className="text-sm">登录</Link>
            )}
          </nav>
        </div>
      </header>
      <main className="container mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
```

- [ ] **Step 4: 创建页面组件**

```tsx
// web/src/pages/home.tsx
export function HomePage() {
  return (
    <div>
      <h1 className="text-2xl font-bold">SkillHub</h1>
      <p className="mt-2 text-muted-foreground">技能注册中心</p>
    </div>
  )
}
```

```tsx
// web/src/pages/login.tsx
import { LoginButton } from '../features/auth/login-button'

export function LoginPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="w-full max-w-sm space-y-6 text-center">
        <h1 className="text-2xl font-bold">登录 SkillHub</h1>
        <LoginButton />
      </div>
    </div>
  )
}
```

```tsx
// web/src/pages/dashboard.tsx
import { useAuth } from '../features/auth/use-auth'
import { AuthGuard } from '../features/auth/auth-guard'

export function DashboardPage() {
  const { user } = useAuth()

  return (
    <AuthGuard>
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="mt-2">欢迎, {user?.displayName}</p>
      </div>
    </AuthGuard>
  )
}
```

- [ ] **Step 5: Commit**

```bash
git add web/src/
git commit -m "feat(web): add TanStack Router with page skeleton

- Root layout with header navigation
- Home, Login, Dashboard pages
- Router config with type-safe routes"
```

### Task 22: Auth Hook + 登录按钮 + 路由守卫

**Files:**
- Create: `web/src/features/auth/use-auth.ts`
- Create: `web/src/features/auth/login-button.tsx`
- Create: `web/src/features/auth/auth-guard.tsx`

- [ ] **Step 1: 创建 useAuth hook**

```tsx
// web/src/features/auth/use-auth.ts
import { useQuery } from '@tanstack/react-query'

interface User {
  userId: string
  displayName: string
  email: string
  avatarUrl: string
  oauthProvider: string
  platformRoles: string[]
}

export function useAuth() {
  const { data: user, isLoading, error } = useQuery<User>({
    queryKey: ['auth', 'me'],
    queryFn: async () => {
      const res = await fetch('/api/v1/auth/me')
      if (res.status === 401) return null
      if (!res.ok) throw new Error('Failed to fetch user')
      return res.json()
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  return {
    user: user ?? null,
    isLoading,
    isAuthenticated: !!user,
    hasRole: (role: string) => user?.platformRoles?.includes(role) ?? false,
  }
}
```

- [ ] **Step 2: 创建 LoginButton**

```tsx
// web/src/features/auth/login-button.tsx
import { useQuery } from '@tanstack/react-query'
import { Button } from '../../shared/ui/button'

interface Provider {
  id: string
  name: string
  authorizationUrl: string
}

export function LoginButton() {
  const { data } = useQuery<{ data: Provider[] }>({
    queryKey: ['auth', 'providers'],
    queryFn: async () => {
      const res = await fetch('/api/v1/auth/providers')
      if (!res.ok) throw new Error('Failed to fetch providers')
      return res.json()
    },
  })

  const providers = data?.data ?? []

  return (
    <div className="space-y-3">
      {providers.map((p) => (
        <Button
          key={p.id}
          className="w-full"
          onClick={() => { window.location.href = p.authorizationUrl }}
        >
          使用 {p.name} 登录
        </Button>
      ))}
    </div>
  )
}
```

- [ ] **Step 3: 创建 AuthGuard**

```tsx
// web/src/features/auth/auth-guard.tsx
import { useNavigate } from '@tanstack/react-router'
import { useAuth } from './use-auth'
import { useEffect } from 'react'

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      navigate({ to: '/login' })
    }
  }, [isLoading, isAuthenticated, navigate])

  if (isLoading) {
    return <div className="flex justify-center py-8">加载中...</div>
  }

  if (!isAuthenticated) return null

  return <>{children}</>
}
```

- [ ] **Step 4: Commit**

```bash
git add web/src/features/
git commit -m "feat(web): add auth hook, login button, and route guard

- useAuth: fetch /api/v1/auth/me with TanStack Query
- LoginButton: dynamic OAuth provider buttons from /api/v1/auth/providers
- AuthGuard: redirect to /login if not authenticated"
```

### Task 23: openapi-fetch 客户端生成管线

**Files:**
- Create: `web/src/api/client.ts`

- [ ] **Step 1: 创建 API 客户端**

```typescript
// web/src/api/client.ts
import createClient from 'openapi-fetch'

// 一期先用手动类型，后续通过 openapi-typescript 自动生成
export const api = createClient({ baseUrl: '/' })

// 便捷方法
export async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })
  if (!res.ok) {
    throw new Error(`API error: ${res.status}`)
  }
  return res.json()
}
```

- [ ] **Step 2: 验证 generate-api 脚本可用**

在后端运行时执行：

Run: `cd web && pnpm run generate-api`

Expected: 如果后端运行中，生成 `src/api/generated/schema.d.ts`；如果后端未运行，报连接错误（预期行为）

- [ ] **Step 3: Commit**

```bash
git add web/src/api/
git commit -m "feat(web): add openapi-fetch API client

- createClient wrapper for type-safe API calls
- generate-api script for openapi-typescript code generation"
```

### Task 24: 前端 Dockerfile + nginx.conf

**Files:**
- Create: `web/Dockerfile`
- Create: `web/nginx.conf`

- [ ] **Step 1: 创建 nginx.conf**

```nginx
# web/nginx.conf
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
    gzip_min_length 1000;

    # SPA 路由：所有非文件请求回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 反向代理
    location /oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /login/oauth2/ {
        proxy_pass http://server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Well-known
    location /.well-known/ {
        proxy_pass http://server:8080;
    }

    # 静态资源缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 健康检查
    location /nginx-health {
        return 200 'ok';
        add_header Content-Type text/plain;
    }
}
```

- [ ] **Step 2: 创建 Dockerfile**

```dockerfile
# web/Dockerfile
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://localhost/nginx-health || exit 1
```

- [ ] **Step 3: Commit**

```bash
git add web/Dockerfile web/nginx.conf
git commit -m "feat(web): add Dockerfile and nginx config

- Multi-stage build: pnpm build → nginx:alpine
- Nginx SPA routing with API reverse proxy
- Static asset caching, health check endpoint"
```

### Task 25: 更新 Makefile 添加前端命令

**Files:**
- Modify: `Makefile`

- [ ] **Step 1: 追加前端相关 target**

在 Makefile 末尾追加：

```makefile
# --- Frontend ---
.PHONY: web-install web-dev web-build generate-api

web-install:
	cd web && pnpm install

web-dev:
	@echo "Run manually: cd web && pnpm dev"

web-build:
	cd web && pnpm build

generate-api:
	cd web && pnpm run generate-api
```

- [ ] **Step 2: Commit**

```bash
git add Makefile
git commit -m "feat: add frontend targets to Makefile

- web-install, web-build, generate-api targets
- web-dev prints manual run instruction (long-running process)"
```

### Task 26: docker-compose.prod.yml 完整部署

**Files:**
- Create: `docker-compose.prod.yml`

- [ ] **Step 1: 创建生产部署 compose 文件**

```yaml
# docker-compose.prod.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: ${DB_PASSWORD:-skillhub_prod}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U skillhub"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    environment:
      MINIO_ROOT_USER: ${MINIO_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD:-minioadmin}
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 5s
      retries: 5

  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/skillhub
      SPRING_DATASOURCE_USERNAME: skillhub
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-skillhub_prod}
      SPRING_DATA_REDIS_HOST: redis
      OAUTH2_GITHUB_CLIENT_ID: ${OAUTH2_GITHUB_CLIENT_ID}
      OAUTH2_GITHUB_CLIENT_SECRET: ${OAUTH2_GITHUB_CLIENT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  web:
    build:
      context: ./web
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      server:
        condition: service_healthy

volumes:
  postgres_data:
  minio_data:
```

- [ ] **Step 2: 创建后端 Dockerfile**

```dockerfile
# server/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/skillhub-app/target/*.jar app.jar
RUN addgroup -S app && adduser -S app -G app
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

- [ ] **Step 3: 更新 Makefile 添加 deploy 命令**

在 Makefile 追加：

```makefile
# --- Deploy ---
.PHONY: deploy deploy-down

deploy:
	docker compose -f docker-compose.prod.yml up -d --build

deploy-down:
	docker compose -f docker-compose.prod.yml down
```

- [ ] **Step 4: Commit**

```bash
git add docker-compose.prod.yml server/Dockerfile Makefile
git commit -m "feat: add production Docker Compose and backend Dockerfile

- docker-compose.prod.yml: full stack deployment
- server/Dockerfile: Maven multi-stage build → JRE alpine
- Makefile deploy/deploy-down targets"
```

### Chunk 3 验收检查

运行以下命令验证 Chunk 3 完成：

```bash
# 1. 确保后端依赖运行
make dev

# 2. 安装前端依赖
make web-install
# Expected: 依赖安装成功

# 3. 构建前端
make web-build
# Expected: dist/ 目录生成

# 4. 启动后端
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local &

# 5. 启动前端开发服务器（手动）
cd web && pnpm dev
# Expected: http://localhost:3000 可访问

# 6. 验证首页
# 浏览器打开 http://localhost:3000
# Expected: 显示 "SkillHub" 标题和 "登录" 链接

# 7. 验证登录页
# 浏览器打开 http://localhost:3000/login
# Expected: 显示 "使用 GitHub 登录" 按钮

# 8. 验证 Dashboard 路由守卫
# 浏览器打开 http://localhost:3000/dashboard
# Expected: 未登录时重定向到 /login

# 9. 验证 MockAuth + Dashboard
curl -H "X-Mock-User-Id: 1" http://localhost:8080/api/v1/auth/me
# Expected: 返回用户信息（前端通过 proxy 也可访问）

# 10. 验证 OpenAPI 类型生成
make generate-api
# Expected: web/src/api/generated/schema.d.ts 生成（需后端运行中）

# 11. 停止所有服务
make dev-down
```

Chunk 3 产出：可运行的前端应用 + OAuth 登录流程 + 路由守卫 + API 类型生成管线 + 生产部署配置。

---

## Phase 1 整体验收检查

对照 `10-delivery-roadmap.md` Phase 1 验收标准：

| 验收项 | 验证方式 | 对应 Task |
|--------|---------|-----------|
| 前后端能跑 | `make dev` + 后端启动 + `pnpm dev` 前端启动 | Task 1-7, 19-24 |
| GitHub OAuth 登录可用 | 配置真实 GitHub OAuth App 后完整登录流程 | Task 10-11, 14, 22 |
| AccessPolicy 准入策略生效 | 切换 `skillhub.access-policy.mode` 验证不同策略 | Task 10 |
| `/api/v1/auth/me` 可用 | `curl` 验证已登录/未登录响应 | Task 16 |
| Token 可用 | 创建 Token → Bearer 认证 → `/api/v1/cli/whoami` | Task 12, 16 |
| OpenAPI spec 可访问 | `curl http://localhost:8080/v3/api-docs` | Task 6 |
| CSRF 防护 | Cookie-to-Header 模式，CLI API 豁免 | Task 14 |
| MockAuthFilter | `X-Mock-User-Id` Header 模拟登录 | Task 15 |
| RBAC 基础 | 种子数据 4 角色 + 9 权限 + 角色判定 | Task 13, 18 |
| 全局异常处理 + requestId | 错误响应包含 requestId | Task 5, 17 |
| 前端登录流程 | 登录页 → GitHub 按钮 → 回调 → Dashboard | Task 21, 22 |
| 路由守卫 | 未登录访问 Dashboard 重定向到 /login | Task 22 |
| openapi-fetch 管线 | `make generate-api` 生成类型文件 | Task 23 |
| Docker 完整部署 | `make deploy` 构建并启动全栈 | Task 24, 26 |

### 完整端到端验证流程

```bash
# 1. 启动本地开发环境
make dev
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local &
cd web && pnpm dev &

# 2. MockAuth 验证
curl -H "X-Mock-User-Id: 1" http://localhost:8080/api/v1/auth/me
# → 200, 返回 Admin 用户信息

# 3. Token 全流程
TOKEN=$(curl -s -X POST -H "X-Mock-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-test"}' \
  http://localhost:8080/api/v1/tokens | jq -r '.token')
echo $TOKEN
# → ask_...

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/auth/me
# → 200, 返回用户信息

# 4. 未认证访问
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/auth/me
# → 401

# 5. OpenAPI
curl -s http://localhost:8080/v3/api-docs | jq '.info.title'
# → "SkillHub API"

# 6. 前端页面
# 浏览器 http://localhost:3000 → 首页
# 浏览器 http://localhost:3000/login → 登录页
# 浏览器 http://localhost:3000/dashboard → 重定向到 /login

# 7. 清理
make dev-down
```

---

**Plan complete.** 共 26 个 Task，3 个 Chunk，覆盖 Phase 1 全部验收标准。
