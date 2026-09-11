# BookNest 前端镜像（多阶段构建：Node 构建 → Nginx 托管）
FROM node:22-alpine AS builder
WORKDIR /app

# 依赖层单独缓存，源码变更不重新拉包
# 带上 package-lock.json 并用 npm ci：保证容器内依赖与开发机完全一致
# （只用 npm install 会无视锁文件，装出与本地不同的版本组合）
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
