# ============================================================
# IoT Platform - 项目入口 Makefile
# 适用: Linux / macOS 原生,Windows 需 WSL / Git Bash
# ============================================================

SHELL := /bin/bash
PROJECT_ROOT := $(shell pwd)
DEPLOY_DIR   := $(PROJECT_ROOT)/deploy
BACKEND_DIR  := $(PROJECT_ROOT)/backend
FRONTEND_DIR := $(PROJECT_ROOT)/frontend
SIM_DIR      := $(PROJECT_ROOT)/tools/iot-device-simulator

# 兼容 docker compose v1 / v2
COMPOSE := $(shell docker compose version >/dev/null 2>&1 && echo "docker compose" || echo "docker-compose")

.DEFAULT_GOAL := help

# 颜色(终端不支持时自动跳过)
GREEN  := \033[32m
YELLOW := \033[33m
CYAN   := \033[36m
RESET  := \033[0m

.PHONY: help
help: ## 显示所有可用命令
	@echo "$(CYAN)IoT Platform - 常用命令$(RESET)"
	@echo ""
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-18s$(RESET) %s\n", $$1, $$2}'

# ============================================================
# 开发环境
# ============================================================

.PHONY: dev-infra
dev-infra: ## 启动中间件 (MySQL/TDengine/EMQX)
	@echo "$(YELLOW)▶ 启动中间件...$(RESET)"
	cd $(DEPLOY_DIR) && $(COMPOSE) --env-file .env.dev up -d mysql tdengine emqx
	@echo "$(GREEN)✓ 中间件已启动$(RESET)"
	@echo "  MySQL:    localhost:33402"
	@echo "  TDengine: localhost:33403 (RESTful) / :33404 (Native)"
	@echo "  EMQX:     localhost:33405 (MQTT) / :33409 (Dashboard)"

.PHONY: dev-infra-down
dev-infra-down: ## 停止中间件
	cd $(DEPLOY_DIR) && $(COMPOSE) --env-file .env.dev stop mysql tdengine emqx

.PHONY: dev-backend
dev-backend: ## 启动后端 (本地 mvn,需先 dev-infra)
	@if ! curl -sf -o /dev/null http://localhost:33402 2>/dev/null; then \
	  echo "$(YELLOW)⚠ MySQL 未响应,请先执行 make dev-infra$(RESET)"; \
	fi
	./scripts/start-backend.sh

.PHONY: dev-frontend
dev-frontend: ## 启动前端 (本地 Vite dev)
	./scripts/start-frontend.sh

.PHONY: dev-all
dev-all: ## 全栈容器化(含 Remote Debug,5005 端口)
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev up -d --build
	@echo "$(GREEN)✓ 全栈已启动$(RESET)"
	@echo "  Frontend: http://localhost:33411"
	@echo "  Backend:  http://localhost:33412/api"
	@echo "  Debug:    localhost:5005 (JDWP)"

.PHONY: dev-down
dev-down: ## 停止全栈容器
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev down

# ============================================================
# 生产环境
# ============================================================

.PHONY: prod
prod: ## 生产部署(需先有 .env.prod)
	@if [ ! -f $(DEPLOY_DIR)/.env.prod ]; then \
	  echo "❌ $(DEPLOY_DIR)/.env.prod 不存在"; \
	  echo "   请先: cp $(DEPLOY_DIR)/.env.example $(DEPLOY_DIR)/.env.prod 并修改所有密码"; \
	  exit 1; \
	fi
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d
	@echo "$(GREEN)✓ 生产环境已启动$(RESET)"

.PHONY: prod-build
prod-build: ## 重新构建镜像后部署
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build

.PHONY: prod-pull
prod-pull: ## 拉取最新镜像并部署(假设镜像已推送到仓库)
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod pull
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d

.PHONY: prod-down
prod-down: ## 停止生产服务(保留数据)
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod down

.PHONY: prod-clean
prod-clean: ## 停止并清理数据卷(慎用!)
	@echo "$(YELLOW)⚠ 将删除所有数据卷,确认吗? [y/N]$(RESET)"
	@read -r ans; [ "$$ans" = "y" ] || exit 1
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod down -v

# ============================================================
# 通用运维
# ============================================================

.PHONY: ps
ps: ## 查看服务状态
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev ps

.PHONY: logs
logs: ## 查看 dev 全栈日志
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev logs -f

.PHONY: logs-prod
logs-prod: ## 查看 prod 全栈日志
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod logs -f

.PHONY: logs-backend
logs-backend: ## 仅查看后端日志
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev logs -f backend

.PHONY: restart-backend
restart-backend: ## 重启后端
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev restart backend

.PHONY: shell-backend
shell-backend: ## 进入后端容器
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev exec backend sh

.PHONY: mysql-cli
mysql-cli: ## 连接 MySQL 容器
	cd $(DEPLOY_DIR) && $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev exec mysql mysql -uiot -piot123456 iot_platform

# ============================================================
# 构建 / 测试 / 清理
# ============================================================

.PHONY: build-frontend
build-frontend: ## 本地构建前端 (类型检查)
	cd $(FRONTEND_DIR) && npm run build:check

.PHONY: build-backend
build-backend: ## 本地构建后端 (跳过测试)
	cd $(BACKEND_DIR) && mvn -B clean package -DskipTests

.PHONY: test
test: ## 运行后端测试
	cd $(BACKEND_DIR) && mvn -B test

.PHONY: clean
clean: ## 清理构建产物
	rm -rf $(BACKEND_DIR)/target
	rm -rf $(FRONTEND_DIR)/dist
	rm -rf $(FRONTEND_DIR)/node_modules/.vite
	@echo "$(GREEN)✓ 已清理$(RESET)"

# ============================================================
# Python 设备模拟器 (tools/iot-device-simulator)
# ============================================================

.PHONY: sim
sim: ## 启动 Python 设备模拟器 GUI (需先 sim-install)
	@if [ -z "$$DISPLAY" ] && [ "$$(uname)" = "Linux" ] && [ -z "$$WAYLAND_DISPLAY" ]; then \
	  echo "$(YELLOW)⚠ 未检测到图形界面 (DISPLAY/WAYLAND_DISPLAY 都为空)$(RESET)"; \
	  echo "  如在远程/无 GUI 环境,请用 X11 转发 或改在本地运行"; \
	fi
	cd $(SIM_DIR) && ./run.sh

.PHONY: sim-install
sim-install: ## 安装 Python 模拟器依赖 (开发模式)
	@echo "$(YELLOW)▶ 安装 Python 设备模拟器...$(RESET)"
	cd $(SIM_DIR) && python3 -m venv .venv 2>/dev/null || true
	cd $(SIM_DIR) && . .venv/bin/activate && pip install -e ".[dev]"
	@echo "$(GREEN)✓ 安装完成,运行 'make sim' 启动$(RESET)"

.PHONY: sim-build
sim-build: ## 打包 Python 模拟器为单可执行文件
	cd $(SIM_DIR) && . .venv/bin/activate 2>/dev/null; \
	  pip install -e ".[packaging]" && ./scripts/build.sh
	@echo "$(GREEN)✓ 产物: $(SIM_DIR)/dist/iot-device-simulator$(RESET)"

.PHONY: sim-test
sim-test: ## 运行 Python 模拟器测试
	cd $(SIM_DIR) && . .venv/bin/activate && pytest

.PHONY: sim-clean
sim-clean: ## 清理模拟器构建/虚拟环境
	rm -rf $(SIM_DIR)/.venv
	rm -rf $(SIM_DIR)/dist
	rm -rf $(SIM_DIR)/build
	rm -rf $(SIM_DIR)/src/*.egg-info
	@echo "$(GREEN)✓ 模拟器产物已清理$(RESET)"