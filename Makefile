# project-govern 根目录 Makefile
# 一键跑开发 / 测试 / 部署 / seed 等常用任务
# 用法: make <target>  或  make help

# ---------------------------------------------------------------
# 元信息
# ---------------------------------------------------------------
.DEFAULT_GOAL := help
SHELL         := /bin/bash

# ---------------------------------------------------------------
# 可调参数(命令行覆盖)
#   make seed-crm-gantt PGHOST=...
#   make db-views-check AUTO_YES=1
# ---------------------------------------------------------------
PGHOST     ?= localhost
PGPORT     ?= 5432
PGUSER     ?= project_govern
PGDATABASE ?= project_govern
# PGPASSWORD 走环境变量,Makefile 不出现明文

# ---------------------------------------------------------------
# 帮助
# ---------------------------------------------------------------
.PHONY: help
help: ## 显示所有 target
	@awk 'BEGIN {FS = ":.*?## "} \
	  /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}' \
	  $(MAKEFILE_LIST)

# ---------------------------------------------------------------
# 依赖 / 安装
# ---------------------------------------------------------------
.PHONY: install
install: ## 装前端依赖(后端走 mvn)
	cd frontend && pnpm install

.PHONY: docker-up
docker-up: ## docker compose 起 PG + 后端 + 前端
	docker compose up -d

.PHONY: docker-down
docker-down: ## docker compose 关停
	docker compose down

# ---------------------------------------------------------------
# 开发
# ---------------------------------------------------------------
.PHONY: dev-backend
dev-backend: ## 跑后端(Spring Boot)
	cd backend && mvn -B spring-boot:run

.PHONY: dev-frontend
dev-frontend: ## 跑前端(Vite)
	cd frontend && pnpm dev

.PHONY: test
test: ## 跑后端单元测试
	cd backend && mvn -B test

.PHONY: ci-local
ci-local: ## 本地模拟 CI 流程(后端单测 + 前端 build)
	$(MAKE) test
	cd frontend && npm ci --no-audit --no-fund && npm run build

.PHONY: e2e
e2e: ## 跑 Cypress e2e
	pnpm e2e

.PHONY: smoke
smoke: ## API 烟雾测试(shell)
	bash docs/api-testing/smoke.sh

.PHONY: smoke-im
smoke-im: ## IM 三通道烟雾测试
	bash scripts/im-smoke/im-smoke.sh

.PHONY: smoke-business
smoke-business: ## 业务烟雾(项目/任务/工时)
	bash scripts/business-smoke.sh

# ---------------------------------------------------------------
# 数据库健康 / 迁移
# ---------------------------------------------------------------
.PHONY: db-views-check
db-views-check: ## 校验 P2.B 依赖视图
	PGHOST=$(PGHOST) PGPORT=$(PGPORT) PGUSER=$(PGUSER) \
	PGDATABASE=$(PGDATABASE) \
	bash scripts/db-views-healthcheck.sh

.PHONY: migration-rollback
migration-rollback: ## Flyway 迁移回滚(谨慎)
	bash scripts/migration/rollback.sh

.PHONY: migration-cutover
migration-cutover: ## 切流脚本(谨慎)
	bash scripts/migration/cutover.sh

# ---------------------------------------------------------------
# Seed 数据
# ---------------------------------------------------------------
.PHONY: seed-list
seed-list: ## 列出所有可用 seed
	@ls -1 docs/seeds/*.sql 2>/dev/null | xargs -n1 basename | \
	  awk 'BEGIN{print "  \033[36m可用 seed 文件:\033[0m"} {print "    " $$0}'

.PHONY: seed-crm-gantt
seed-crm-gantt: ## 执行客户CRM系统(id=3)甘特图样例
	PGHOST=$(PGHOST) PGPORT=$(PGPORT) PGUSER=$(PGUSER) \
	PGDATABASE=$(PGDATABASE) \
	bash scripts/seed-crm-gantt.sh

.PHONY: seed-crm-gantt-rollback
seed-crm-gantt-rollback: ## 回滚客户CRM系统样例
	PGHOST=$(PGHOST) PGPORT=$(PGPORT) PGUSER=$(PGUSER) \
	PGDATABASE=$(PGDATABASE) \
	bash scripts/seed-crm-gantt.sh --rollback

# ---------------------------------------------------------------
# 清理
# ---------------------------------------------------------------
.PHONY: clean
clean: ## 清前后端构建产物
	rm -rf backend/target frontend/dist

# ---------------------------------------------------------------
# 文档规范检查(STATUS/WBS 双轨 + 文件头校验 + 链接完整性)
# ---------------------------------------------------------------
DOCS_DIR   := docs
DOCS_SCRIPT := scripts/docs-lint.sh

.PHONY: docs-lint
docs-lint: ## 校验 docs/ 目录规范(文件头 + 死链接 + STATUS/WBS 双轨边界)
	@bash $(DOCS_SCRIPT) $(DOCS_DIR)

.PHONY: docs-lint-install
docs-lint-install: ## 安装 docs-lint 依赖(无外部依赖,仅确保脚本可执行)
	@install -m 0755 scripts/docs-lint.sh $(DOCS_SCRIPT)

.PHONY: docs-lint-clean
docs-lint-clean: ## 清理 docs-lint 临时输出
	@rm -rf .docs-lint-cache
