# PMO PMS Seed 数据集

> 收集**手动执行**的种子数据 SQL,用于演示 / 培训 / 联调 / 补数据。
> 区别于 Flyway 迁移(`backend/src/main/resources/db/migration-pg/`),本目录
> 的脚本是**可选、按需执行**,不进版本升级链。

## 文件索引

| 文件 | 适用场景 | 影响行数 | 风险 | 回滚 |
|------|----------|----------|------|------|
| [`2026-06-08-crm-gantt-seed.sql`](./2026-06-08-crm-gantt-seed.sql) | ⚠️  **DEPRECATED** — hardcode id=3/64-68,在 5432 库会被 e2e fixture 撞掉 | 1 project + 5 milestone | — | 见文件头 |

## 使用约定

### 命名
- 格式: `YYYY-MM-DD-<业务含义>-seed.sql`
- 例: `2026-06-08-crm-gantt-seed.sql`

### 编写要求
1. **首部注释** — 写明目的 / 风险 / 回滚位置
2. **事务包裹** — `BEGIN; ... COMMIT;`,出异常自动回滚
3. **WHERE 限定** — 必须带主键 + `deleted = FALSE`,避免误改
4. **不改字典 / 关联表** — 字典已有,工时/通知不动
5. **文末验证 SQL** — 给执行人一条命令直接看结果
6. **文末回滚 SQL** — 用 `-- BEGIN; ... COMMIT;` 注释,出问题时手动启用

### 执行

```bash
# 标准方式
psql -h <host> -U pmo_pms -d pmo_pms -f <seed-file>.sql

# 事务+回滚式(先 dry-run 看影响行数)
psql -h <host> -U pmo_pms -d pmo_pms --single-transaction --variable=ON_ERROR_STOP=1 \
  -f <seed-file>.sql
```

### 不在 seed 范围
- **字典初始化** — 走 Flyway `migration-pg/`
- **测试夹具** — 走 `backend/src/test/resources/`
- **定期批跑数据** — 走 `backend/src/main/java/.../job/`

## 待补充

| 业务 | 拟用文件名 | 状态 |
|------|-----------|------|
| 工时周报样例 | `2026-06-XX-timesheet-week-demo.sql` | 规划中 |
| 通知中心样例 | `2026-06-XX-notification-demo.sql` | 规划中 |
| 风险登记册样例 | (等 P3 风险模块上线) | 规划中 |
| 字典扩展(P3 成本类) | (等 P3 实施) | 规划中 |
