---
status: draft
created: 2026-08-11
updated: 2026-08-11
summary: 部署指南 - Docker Compose / Kubernetes / Nginx 反代
---

# 部署指南 (Deployment)

## 1. Docker Compose (单节点 / 小团队)

`docker-compose.yml` 包含:
- MySQL 8.0 (端口 3306)
- PostgreSQL 16 (端口 5432, CI 验证用)
- Redis 7 (端口 6379)
- Backend (端口 8080)
- Frontend Nginx (端口 80)
- DingTalk Mock (可选)

启动:
```bash
make dev-up
make dev-down
make dev-logs
```

数据持久化: `docker volumes` (`mysql_data`, `redis_data`).

## 2. Kubernetes (生产)

`k8s/` 目录:
- `namespace.yaml` — `pmo-pms`
- `backend-deployment.yaml` — 2 replicas + readiness probe
- `frontend-deployment.yaml` — Nginx 静态 + 反代 `/api`
- `mysql-statefulset.yaml` — 持久卷 + 备份 cron
- `redis-statefulset.yaml` — Sentinel/Cluster
- `ingress.yaml` — HTTPS + Let's Encrypt

部署:
```bash
kubectl apply -f k8s/
kubectl -n pmo-pms get pods
```

## 3. Nginx 反代

`/etc/nginx/conf.d/pmo-pms.conf`:

```nginx
upstream backend {
    server 127.0.0.1:8080;
}

server {
    listen 80;
    server_name pmo.example.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name pmo.example.com;

    ssl_certificate /etc/letsencrypt/live/pmo.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pmo.example.com/privkey.pem;

    client_max_body_size 20m;

    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # SSE 支持
        proxy_buffering off;
        proxy_read_timeout 86400;
    }

    location / {
        root /var/www/pmo-pms/frontend/dist;
        try_files $uri $uri/ /index.html;
    }
}
```

## 4. HTTPS

Let's Encrypt (certbot):
```bash
certbot certonly --nginx -d pmo.example.com
```

自动续期: `certbot renew --cron`.

## 5. 备份

- MySQL: 每日 `mysqldump` → S3 (保留 30 天)
- PG: 每周 `pg_dump` → S3 (CI 验证用, 不关键)
- Redis: AOF 每秒持久化

## 6. 监控

- Prometheus + Grafana (P2 计划)
- 关键指标: API P95 延迟 / 错误率 / MySQL 慢查询 / 审批引擎队列长度