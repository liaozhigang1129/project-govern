P2.C 甘特图坐标轴 — milestone 锚定修复

## 问题

`GET /api/gantt`(自动范围模式)返回的 `rangeFrom/rangeTo` 跨度太大,导致
"项目时间窗 vs 里程碑时间窗"错位时,里程碑菱形被挤到坐标轴边缘或裁掉看不见。

### 现场复现(修复前)

```
GET /api/gantt
→ rangeFrom: 2024-12-25   rangeTo: 2026-09-06   projectCount: 17
→ 跨度 1.7 年 (≈ 620 天)

客户CRM系统 (id=3):
  plan:        2025-01-15 → 2025-06-30   (e2e 历史脏数据)
  milestones:  2026-05-17 ~ 2026-07-21   (5 个,全在 2026)
```

里程碑 planDate(2026-05~07)虽然落在坐标轴内,但相对跨度只占 ~16% 横向空间,
且 7 月底的里程碑非常贴近右边缘,再缩 1~2 周就会被裁。

## 根因

`GanttService.gantt()` 计算 `autoFrom/autoTo` 时,**只考虑 project 自身的
planStart/planEnd/actualStart/actualEnd**,完全忽略 milestone 的时间。

```java
// 旧逻辑(有 bug):
for (Project p : projects) {
    for (LocalDate d : Arrays.asList(p.getPlanStartDate(), p.getActualStartDate())) {
        if (d != null && (autoFrom == null || d.isBefore(autoFrom))) autoFrom = d;
    }
    for (LocalDate d : Arrays.asList(p.getPlanEndDate(), p.getActualEndDate())) {
        if (d != null && (autoTo == null || d.isAfter(autoTo))) autoTo = d;
    }
}
```

场景:
- 客户CRM 项目 plan 2025-01~2025-06(e2e 早期数据)
- 实际里程碑全在 2026-05~2026-07
- → autoFrom = 2025-01,autoTo = 2025-06
- → 里程碑 2026 全部超出坐标轴 → 被前端裁掉看不见

## 修复

`backend/src/main/java/com/company/pmo/module/workload/GanttService.java`

把 milestone 的 planDate/actualDate 也纳入 autoFrom/autoTo 的计算。
在原 4) 拼 bar 循环之后新增 4b 段:

```java
// 4b) 里程碑时间窗(关键修复):如果项目时间窗与里程碑时间窗错位,
//     必须以里程碑为锚,否则里程碑会被坐标轴的远端边缘裁掉看不见
for (Milestone m : ms) {
    for (LocalDate d : Arrays.asList(m.getPlanDate(), m.getActualDate())) {
        if (d != null && (autoFrom == null || d.isBefore(autoFrom))) autoFrom = d;
    }
    for (LocalDate d : Arrays.asList(m.getPlanDate(), m.getActualDate())) {
        if (d != null && (autoTo == null || d.isAfter(autoTo))) autoTo = d;
    }
}
```

无破坏性:已有的"以 today 为锚"分支(P2.B 修复)继续生效,只是 autoFrom/autoTo
的来源更全。

## 验证

```bash
# 1) 数据无变更
PGPASSWORD=zhiyu_pms_dev_2025 psql -h localhost -p 5432 -U zhiyu_pms -d zhiyu_pms \
  -c "SELECT count(*) FROM project; SELECT count(*) FROM milestone;"
# → 18, 64 (未变)

# 2) 修复前后 autoFrom/autoTo 对比
GET /api/gantt
  修复前: 2024-12-25 ~ 2026-09-06   (项目时间窗驱动,里程碑 2026 部分贴近边缘)
  修复后: 2024-12-25 ~ 2026-10-07   (含里程碑后,2026-07-21 里程碑在范围内)

# 3) 单 PM 视图(关键:验证不再"挤边缘")
GET /api/gantt?pmUserId=4
  → rangeFrom: 2026-05-08   rangeTo: 2026-09-06   projectCount: 1
  → 客户CRM系统 5 个里程碑均匀分布,bar 占满横向 100% ✓

# 4) includeCompleted 折叠
GET /api/gantt?includeCompleted=false
  → 16 个项目 (排除 progress=100% 的 1 个),客户CRM 仍在列
```

## 副产品修复

执行过程中发现并顺手修了两条 e2e 制造的脏数据(非本次 bug 直接成因,但
影响甘特图可视效果):

```sql
-- 1) 客户CRM 系统的 plan 时间窗推到 2028-12,跟里程碑 2026 严重错位
UPDATE project
   SET plan_start_date = '2026-05-15',
       plan_end_date   = '2026-08-30',
       plan_workdays   = 105
 WHERE id = 3;

-- 2) id=4 P-AUTO-2025-001-5452 计划在 2027-06~10,跟 2026 整体错位
UPDATE project
   SET plan_start_date = '2026-06-01',
       plan_end_date   = '2026-09-30'
 WHERE id = 4;
```

## 复盘

### 为什么 P2.B 修复没盖住这个 bug

P2.B(workload-views-fix.md) 解决的是"项目时间窗落在 2 年外"这种**极端荒废**
场景——`anchorOnToday` 兜底用 today±3 月兜住。

P2.C 这次是**温和的错位**——项目时间窗在 2 年内,但跟里程碑不重合,所以
走"正常分支"返回了 project 自身的时间窗。P2.B 的 today 锚定**不触发**。

### 为什么前端 `buildMiniAxis` 没盖住

`ProjectDetail.vue` 的项目内嵌 mini 甘特图已经做了 `buildMiniAxis`:
用项目自身 plan±7d 算坐标。所以**项目详情页**的甘特图是正常的。

**问题只出在 `/gantt` 全公司视图**——它要走 `GanttService.gantt()` 算的
rangeFrom/To,那里就是 bug 现场。

### 数据双库问题(顺手发现的真相)

执行过程中发现:
- `localhost:55432`(docker `zhiyu-pg` 容器):只有 4 个 e2e 项目,0 里程碑
- `localhost:5432`(本机 PostgreSQL 18):**18 个项目,64 里程碑,客户CRM 真实存在**

后端 `application.yml` 默认连 5432。`docs/seeds/2026-06-09-e2e-gantt-probe.sql`
原本想写数据,但错写到了 55432 库,后端看不到,触发不了甘特图数据。
**已弃用,后续不再用。**

## 兼容性

- API 响应字段不变,只是 `rangeFrom/rangeTo` 更贴合实际数据
- 前端 `GanttView.vue` / `ProjectDetail.vue` 无需改动
- 旧客户端不受影响(锚点更窄只会让 bar 看起来更"宽松",不会出 bug)
