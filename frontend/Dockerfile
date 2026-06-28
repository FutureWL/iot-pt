# ============================================================
# Frontend Dockerfile - 多阶段构建
# 支持构建参数注入 VITE_* 环境变量:
#   docker build --build-arg VITE_API_BASE_URL=/api ...
# ============================================================

# ---- 阶段 1: 构建 ----
FROM node:20-alpine AS builder
WORKDIR /build

# 镜像源加速
RUN npm config set registry https://registry.npmmirror.com

# 构建参数(默认空值,生产推荐通过 .env.production 注入)
ARG VITE_API_BASE_URL=""
ARG VITE_WS_BASE_URL=""
ARG VITE_APP_TITLE="物联网平台"
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_WS_BASE_URL=$VITE_WS_BASE_URL \
    VITE_APP_TITLE=$VITE_APP_TITLE

COPY package.json ./
RUN npm install --no-audit --no-fund

COPY . .
# 跳过 vue-tsc 严格类型检查,生产环境只做打包(类型检查留给 dev 模式)
RUN npx vite build

# ---- 阶段 2: 运行 ----
FROM nginx:1.31-alpine
WORKDIR /usr/share/nginx/html

# 复制构建产物
COPY --from=builder /build/dist/ ./

# 复制 nginx 配置
COPY nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD wget -qO- http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]