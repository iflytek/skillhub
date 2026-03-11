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
