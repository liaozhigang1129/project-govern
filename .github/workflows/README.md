# 📋 CI / Release Workflows

GitHub Actions 配置 — 知驭 ZhiYu 项目。

---

## 📁 文件结构

```
.github/workflows/
├── maven-test.yml   ← CI 主流程(PR / push 触发)
└── release.yml      ← GitHub Release 自动创建(tag 触发)
```

---

## 🔄 CI 流程 (`maven-test.yml`)

### 触发条件
- push 到 `main` / `develop` / `feature/**`
- 推 `v*` tag
- pull_request 到 `main` / `develop`
- 手动 dispatch

### Job 矩阵

| # | Job | 依赖 | 作用 |
|---|-----|------|------|
| 1 | **backend-test** 🧪 | — | Maven 单测(286/286) + Jacoco |
| 2 | **backend-build** 🏗️ | 1 | 编译 + JAR 打包 |
| 3 | **frontend-build** 🎨 | — | npm ci + vue-tsc + vite build |
| 4 | **integration-smoke** 🚬 | 1, 2 | 起服务 + 健康检查 + login 探测 |
| 5 | **ci-status** 📋 | 1-4 | 汇总状态(必须全部 success) |

### 关键配置
- ✅ Java 17 (Spring Boot 3.3.4)
- ✅ Node 20 (Vue 3 + Vite 8)
- ✅ MySQL 8 service container(贴近生产)
- ✅ Aliyun Maven mirror(CN 网络加速)
- ✅ npmmirror.com registry
- ✅ 完整 fetch-depth(给 Jacoco / version 用)
- ✅ Artifact 上传(Surefire / JAR / dist)
- ✅ Concurrency 控制(同分支取消上次)

### 失败排查
```bash
# 在本地模拟 CI 环境
make ci-local

# 或
cd backend && mvn -B test -Dspring.profiles.active=ci
```

---

## 🚀 Release 流程 (`release.yml`)

### 触发
- 推送 `v*.*.*` tag
- 手动 dispatch 输入 tag

### 自动产出
1. ✅ GitHub Release 页面
2. ✅ Release notes 来自 `RELEASE-NOTES-{tag}.md`
3. ✅ commits 列表(自上一个 tag 起)
4. ✅ JAR artifact 上传(90 天保留)

### 用法

```bash
# 1. 准备 release notes(可选)
vim RELEASE-NOTES-v4.1.0.md

# 2. 打 tag
git tag -a v4.1.0 -m "V4.1.0 ..."
git push origin v4.1.0

# 3. GitHub 自动创建 Release
#    → 浏览器打开 https://github.com/liaozhigang1129/zhiyu-pms/releases/tag/v4.1.0
```

---

## 🔐 所需 Secrets

| Secret | 用途 | 必须 |
|--------|------|------|
| `GITHUB_TOKEN` | 自动生成,无需配置 | ✅ 内置 |
| `CODECOV_TOKEN` | (可选)上传覆盖率到 codecov.io | ❌ |
| `DOCKERHUB_USERNAME` | (未来)Docker 镜像推送 | ❌ |
| `DOCKERHUB_TOKEN` | (未来)Docker 镜像推送 | ❌ |

---

## 📊 当前 CI 状态

> 🟢 **首次配置** — 待 push 后跑通验证

### 期望结果
- backend-test: ✅ ~3-5 分钟
- backend-build: ✅ ~2-3 分钟
- frontend-build: ✅ ~2-3 分钟
- integration-smoke: ✅ ~1 分钟
- ci-status: ✅ 全部 success

---

## 🛠 本地等效命令

```bash
# 跑全部 CI 等效测试
make ci-local
# 等价于:
cd backend && mvn clean test
cd ../frontend && npm ci && npm run build
```

---

> 📅 配置时间:2026-06-13 · 配合 v4.0.0 发布同步建立