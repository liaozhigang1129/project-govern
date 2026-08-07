#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P5-真实 Outcome 反馈循环

目的:
  每晚扫 7+ 天前的 advisory,根据 (status + milestone.actual_date + applied_risk_id)
  算出真实 outcome_severity,UPSERT 到 milestone_ai_outcome 表。
  第二天凌晨 train --incremental 就能学到新数据。

Outcome 规则 (PM 经验公式):
  - REJECTED                  → INFO      (PM 采纳了建议并消除了风险)
  - PENDING + 实际未延期      → INFO
  - APPLIED  + 有 applied_risk → CRITICAL (风险真触发,延期 14+ 天)
  - APPLIED  + 延期 1-14 天   → WARNING
  - APPLIED  + 按时完成       → WARNING  (建议被采纳,虽然延期但小)
  - PENDING + 延期 1-14 天    → WARNING
  - PENDING + 延期 14+ 天     → CRITICAL
  - APPLIED + 无延期          → WARNING  (建议过早 / 风险小)
  - 已过期 30+ 天未处理       → CRITICAL

用法:
  # 全量重算
  python3 outcome_recompute.py
  # 仅算 7+ 天前
  python3 outcome_recompute.py --min-age-days 7
  # dry-run
  python3 outcome_recompute.py --dry-run

依赖:  pip3 install mysql-connector-python
"""
import argparse
import sys
import os
import json
from datetime import date, datetime, timedelta
import mysql.connector

OUTCOME_RULES = """
规则 (顺序匹配):
  1) status='REJECTED' + 有 reject_reason=误报 → INFO
  2) status='COMPLETED' + 实际提前或按时完成   → INFO
  3) status='APPLIED' + applied_risk_id 非空    → CRITICAL
  4) 延期天数 = actual_date - plan_date
     - 延期 <= 0  (提前)                          → INFO
     - 1 <= 延期 <= 14                            → WARNING
     - 延期 > 14                                  → CRITICAL
  5) 已创建 30+ 天仍是 PENDING                   → CRITICAL
  6) 其他                                          → WARNING
"""

def compute_outcome(status, reject_reason, plan_date, actual_date, created_at, applied_risk_id, today=None):
    if today is None:
        today = date.today()
    # 规则 1: REJECTED
    if status == "REJECTED":
        if reject_reason and ("误报" in reject_reason or "误判" in reject_reason or "no-such" in (reject_reason or "")):
            return "INFO", "REJECTED_误报"
        # 拒绝说明 PM 觉得没必要 → 大部分情况确实没出问题
        return "INFO", "REJECTED_采纳"
    # 规则 2: COMPLETED 按时 / 提前
    if status == "COMPLETED":
        if plan_date and actual_date:
            lag = (actual_date - plan_date).days
            if lag <= 0:
                return "INFO", f"COMPLETED_提前{abs(lag)}天"
        return "INFO", "COMPLETED"
    # 规则 3: APPLIED + 真触发风险 → CRITICAL
    if status == "APPLIED" and applied_risk_id is not None:
        if plan_date and actual_date:
            lag = (actual_date - plan_date).days
            if lag > 14:
                return "CRITICAL", f"APPLIED_延期{lag}天+风险触发"
        return "CRITICAL", "APPLIED_风险触发"
    # 规则 4: 延期天数
    if plan_date and actual_date:
        lag = (actual_date - plan_date).days
        if lag <= 0:
            return "INFO", f"提前{abs(lag)}天"
        if 1 <= lag <= 14:
            return "WARNING", f"延期{lag}天"
        if lag > 14:
            return "CRITICAL", f"延期{lag}天"
    # 规则 5: PENDING 30+ 天
    if status == "PENDING" and created_at:
        age = (today - created_at.date()).days if isinstance(created_at, datetime) else (today - created_at).days
        if age > 30:
            return "CRITICAL", f"PENDING_{age}天未处理"
    # 规则 6: 其他
    return "WARNING", "未明确"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只打印不算")
    parser.add_argument("--min-age-days", type=int, default=0, help="仅算 N 天前创建")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="pmo_pms")
    parser.add_argument("--password", default="pmo_pms_dev_2025")
    parser.add_argument("--database", default="pmo_pms")
    args = parser.parse_args()
    conn = mysql.connector.connect(
        host=args.host, port=args.port,
        user=args.user, password=args.password,
        database=args.database
    )
    cur = conn.cursor()
    # 1) 建表 (如果不存在)
    cur.execute("""CREATE TABLE IF NOT EXISTS milestone_ai_outcome (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        advisory_id BIGINT NOT NULL,
        outcome_severity VARCHAR(16) NOT NULL,
        outcome_reason VARCHAR(255),
        decided_at DATE,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_advisory (advisory_id)
    )""")
    conn.commit()
    # 2) 拉 advisory (含 milestone)
    sql = """
        SELECT
            a.id, a.status, a.reject_reason, a.created_at, a.applied_risk_id,
            a.decided_at, a.milestone_plan_date,
            m.actual_date
        FROM milestone_ai_advisory a
        LEFT JOIN milestone m ON m.id = a.milestone_id
        WHERE a.deleted = 0
    """
    if args.min_age_days > 0:
        sql += f" AND a.created_at < DATE_SUB(CURDATE(), INTERVAL {args.min_age_days} DAY)"
    cur.execute(sql)
    rows = cur.fetchall()
    print(f"[recompute] scanning {len(rows)} advisories (min-age={args.min_age_days}d)")
    # 3) 算 outcome
    recompute_count = 0
    info_count = warn_count = crit_count = 0
    updates = []
    for (aid, status, rej_reason, created_at, applied_risk_id, decided_at, plan_date, actual_date) in rows:
        outcome, reason = compute_outcome(
            status=status, reject_reason=rej_reason,
            plan_date=plan_date, actual_date=actual_date,
            created_at=created_at, applied_risk_id=applied_risk_id
        )
        if outcome == "INFO":   info_count += 1
        elif outcome == "WARNING": warn_count += 1
        else:                    crit_count += 1
        updates.append((aid, outcome, reason, decided_at.date() if isinstance(decided_at, datetime) else decided_at))
        recompute_count += 1
    print(f"[recompute] outcomes: INFO={info_count} WARNING={warn_count} CRITICAL={crit_count}")
    if args.dry_run:
        # 打印前 5 个
        for u in updates[:5]:
            print(f"  advisory={u[0]} → {u[1]} ({u[2]})")
        print("[recompute] DRY-RUN done, no write")
        return
    # 4) UPSERT
    upsert_sql = """INSERT INTO milestone_ai_outcome
        (advisory_id, outcome_severity, outcome_reason, decided_at)
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            outcome_severity = VALUES(outcome_severity),
            outcome_reason = VALUES(outcome_reason)
    """
    cur.executemany(upsert_sql, updates)
    conn.commit()
    print(f"[recompute] UPSERT {cur.rowcount} rows → milestone_ai_outcome")
    # 5) 统计
    cur.execute("SELECT outcome_severity, COUNT(*) FROM milestone_ai_outcome GROUP BY outcome_severity")
    print("[recompute] table state:")
    for r in cur.fetchall():
        print(f"  {r[0]:8s} = {r[1]}")
    conn.close()
    print("[recompute] DONE")

if __name__ == "__main__":
    main()
