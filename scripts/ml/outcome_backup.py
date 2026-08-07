#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P5-Outcome 表备份
导出最近 N 天 outcome 表到 CSV (审计 + 灾难恢复)
用法: python3 outcome_backup.py --days 7 --output backups/outcome_20250614.csv
"""
import argparse
import csv
import os
import sys
from datetime import datetime, timedelta
import mysql.connector

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--output", required=True)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="pmo_pms")
    parser.add_argument("--password", default="pmo_pms_dev_2025")
    parser.add_argument("--database", default="pmo_pms")
    args = parser.parse_args()
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    conn = mysql.connector.connect(
        host=args.host, port=args.port,
        user=args.user, password=args.password,
        database=args.database
    )
    cur = conn.cursor()
    sql = f"""SELECT o.id, o.advisory_id, a.project_id, a.milestone_id, a.severity,
                     o.outcome_severity, o.outcome_reason, o.decided_at,
                     a.created_at, o.created_at
              FROM milestone_ai_outcome o
              JOIN milestone_ai_advisory a ON a.id = o.advisory_id
              WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL {args.days} DAY)
              ORDER BY o.id DESC"""
    cur.execute(sql)
    rows = cur.fetchall()
    headers = [d[0] for d in cur.description]
    with open(args.output, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(headers)
        for r in rows:
            w.writerow([str(x) if x is not None else "" for x in r])
    print(f"[backup] wrote {len(rows)} rows to {args.output}")
    conn.close()

if __name__ == "__main__":
    main()
