# ===== Stage 1: Build =====
FROM node:20-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/pnpm-lock.yaml* ./
RUN corepack enable && (test -f pnpm-lock.yaml && pnpm install --frozen-lockfile || npm install)
COPY frontend/ .
RUN pnpm build 2>/dev/null || npm run build

# ===== Stage 2: Runtime (nginx 静态) =====
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY deploy/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1
CMD ["nginx","-g","daemon off;"]
