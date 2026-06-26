# ============================================================
# Frontend Dockerfile - 多阶段构建
# ============================================================

# ---- 阶段 1: 构建 ----
FROM node:20-alpine AS builder
WORKDIR /build

# 镜像源加速
RUN npm config set registry https://registry.npmmirror.com

COPY package.json ./
RUN npm install --no-audit --no-fund

COPY . .
# 跳过 vue-tsc 严格类型检查,生产环境只做打包(类型检查留给 dev 模式)
RUN npx vite build

# ---- 阶段 2: 运行 ----
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# 复制构建产物
COPY --from=builder /build/dist/ ./

# 复制 nginx 配置
COPY nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD wget -qO- http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
