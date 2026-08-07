#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P5-真实数据扩充器
真实 MySQL 1 advisory + 生成 1000 行合成 (按 severity 分布)
"""
import mysql.connector
import numpy as np
import json
from datetime import date, timedelta
import sys

np.random.seed(42)

# 真实分布: 60% WARNING + 25% INFO + 15% CRITICAL
SEVERITY_DIST = [("INFO", 0.25), ("WARNING", 0.60), ("CRITICAL", 0.15)]

def main():
    conn = mysql.connector.connect(
        host="localhost", port=3306,
        user="pmo_pms", password="pmo_pms_dev_2025",
        database="pmo_pms"
    )
    cur = conn.cursor()
    print("[seed] start")
    # 1) 拉真实 project/milestone
    cur.execute("SELECT id FROM project ORDER BY id")
    project_ids = [r[0] for r in cur.fetchall()]
    cur.execute("SELECT id, name, project_id, plan_date, actual_date FROM milestone")
    milestones = cur.fetchall()
    print(f"[seed] {len(project_ids)} projects, {len(milestones)} milestones")
    if not project_ids or not milestones:
        print("ERROR: no project/milestone"); sys.exit(1)
    # 2) 生成 1000 行 advisory
    rows = []
    base_date = date(2025, 1, 1)
    for i in range(1000):
        sev = np.random.choice([s for s, _ in SEVERITY_DIST], p=[p for _, p in SEVERITY_DIST])
        # 5 维信号按 severity 强度分布
        if sev == "INFO":
            sig_o, sig_s, sig_pl, sig_v, sig_h = (np.random.uniform(0,30), np.random.uniform(0,20), np.random.uniform(0,20), np.random.uniform(0,30), np.random.uniform(0,30))
            score = round(sig_o*0.3 + sig_s*0.2 + sig_pl*0.2 + sig_v*0.15 + sig_h*0.15, 2)
            outcome = "INFO"
        elif sev == "WARNING":
            sig_o, sig_s, sig_pl, sig_v, sig_h = (np.random.uniform(20,60), np.random.uniform(15,40), np.random.uniform(15,50), np.random.uniform(20,50), np.random.uniform(20,50))
            score = round(sig_o*0.3 + sig_s*0.2 + sig_pl*0.2 + sig_v*0.15 + sig_h*0.15, 2)
            outcome = np.random.choice(["INFO","WARNING"], p=[0.4, 0.6])
        else:  # CRITICAL
            sig_o, sig_s, sig_pl, sig_v, sig_h = (np.random.uniform(60,100), np.random.uniform(40,80), np.random.uniform(50,90), np.random.uniform(40,80), np.random.uniform(40,80))
            score = round(sig_o*0.3 + sig_s*0.2 + sig_pl*0.2 + sig_v*0.15 + sig_h*0.15, 2)
            outcome = np.random.choice(["WARNING","CRITICAL"], p=[0.3, 0.7])
        # 选 milestone
        m_id, m_name, m_pid, m_plan, m_end = milestones[i % len(milestones)]
        # 状态
        m_status = np.random.choice(["ACTIVE","TERMINAL","COMPLETED"], p=[0.5, 0.2, 0.3])
        if m_status == "ACTIVE":
            status_code = "PENDING"
        elif m_status == "TERMINAL":
            status_code = np.random.choice(["APPLIED","REJECTED"], p=[0.7, 0.3])
        else:
            status_code = "APPLIED" if outcome != "INFO" else "COMPLETED"
        # phase
        phase_code = np.random.choice(["DESIGN","BUILD","DEPLOY","UAT"], p=[0.2, 0.3, 0.3, 0.2])
        phase_name = phase_code.capitalize()
        # 上下文 (派生)
        if m_plan:
            plan_date = m_plan
        else:
            plan_date = base_date + timedelta(days=int(np.random.uniform(0, 365)))
        lag_days = (date(2025, 6, 1) - plan_date).days
        confidence = round(np.random.uniform(0.5, 0.95), 2)
        # reasons / suggestions json
        reasons = json.dumps({"top_signal": "OVERDUE" if sig_o > sig_s else "SPI", "days": int(lag_days)}, ensure_ascii=False)
        sugg = json.dumps([{"action":"REVIEW","weight":3},{"action":"ESCALATE","weight":2 if sev!="CRITICAL" else 5}], ensure_ascii=False)
        rows.append((
            m_pid, m_id, None, phase_code, phase_name, m_name, plan_date, m_status,
            sev, score, confidence,
            round(sig_o,2), round(sig_s,2), round(sig_pl,2), round(sig_v,2), round(sig_h,2),
            reasons, sugg,
            np.random.choice(["SCHEDULE","QUALITY","SCOPE","RESOURCE"], p=[0.4, 0.2, 0.2, 0.2]),
            int(2 if sev=="INFO" else (3 if sev=="WARNING" else 4)),
            int(2 if sev=="INFO" else (3 if sev=="WARNING" else 5)),
            status_code, "2025-06-01 00:00:00", "rule-engine-v1.0"
        ))
    # 3) INSERT
    sql = """INSERT INTO milestone_ai_advisory
        (project_id, milestone_id, phase_id, phase_code, phase_name, milestone_name, milestone_plan_date, milestone_status_code,
         severity, score, confidence,
         signal_overdue, signal_spi, signal_phase_lag, signal_velocity, signal_historical,
         reasons_json, suggestions_json,
         category, suggested_probability, suggested_impact,
         status, decided_at, model_version)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s, %s,%s,%s, %s,%s,%s,%s,%s, %s,%s, %s,%s,%s, %s,%s,%s)"""
    cur.executemany(sql, rows)
    conn.commit()
    print(f"[seed] inserted {len(rows)} advisory rows")
    # 4) 同时落 outcome_severity 单独表 (ML 训练用)
    # 先建表 (如果不存在)
    cur.execute("""CREATE TABLE IF NOT EXISTS milestone_ai_outcome (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        advisory_id BIGINT NOT NULL,
        outcome_severity VARCHAR(16) NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )""")
    # 找刚插入的 advisory id
    cur.execute("SELECT id, severity FROM milestone_ai_advisory ORDER BY id DESC LIMIT %s" % len(rows))
    inserted = cur.fetchall()
    # 给每个 advisory 写 outcome (severity → outcome)
    out_rows = []
    for aid, sev in inserted:
        if sev == "INFO":
            out = "INFO"
        elif sev == "WARNING":
            out = np.random.choice(["INFO","WARNING"], p=[0.4, 0.6])
        else:
            out = np.random.choice(["WARNING","CRITICAL"], p=[0.3, 0.7])
        out_rows.append((aid, out))
    cur.executemany("INSERT INTO milestone_ai_outcome (advisory_id, outcome_severity) VALUES (%s,%s)", out_rows)
    conn.commit()
    print(f"[seed] inserted {len(out_rows)} outcome rows")
    cur.execute("SELECT outcome_severity, COUNT(*) FROM milestone_ai_outcome GROUP BY outcome_severity")
    for r in cur.fetchall():
        print(f"  outcome: {r[0]} = {r[1]}")
    conn.close()
    print("[seed] DONE")

if __name__ == "__main__":
    main()
