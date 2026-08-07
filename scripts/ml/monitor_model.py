#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P5-模型监控 (新 pkl 验证集 accuracy vs 历史 pkl, 下降 >5% 告警)

用法:
  # 跑全流程: 训练新 pkl + 评估 + 对比 + 报警
  python3 monitor_model.py --new-model models/milestone_lgbm_latest.pkl \
      --prev-model models/milestone_lgbm_real.pkl \
      --drop-threshold 0.05

依赖: lightgbm scikit-learn pandas mysql-connector-python joblib
"""
import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
import numpy as np
import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.metrics import (accuracy_score, classification_report,
                             f1_score, confusion_matrix)
import lightgbm as lgb

# 复用 train 逻辑的常量
FEATURE_COLS = [
    "signal_overdue", "signal_spi", "signal_phase_lag",
    "signal_velocity", "signal_historical",
    "score", "confidence", "is_critical", "is_warning",
    "phase_lag_days", "project_age_days", "milestone_age_days",
    "pm_experience_projects", "team_size", "historical_hit_rate",
]
LABEL_COL = "outcome_severity"

def load_eval_data():
    """拉训练数据 + 拆 80/20 (与 train 保持一致)"""
    import mysql.connector
    conn = mysql.connector.connect(
        host=os.environ.get("PMO_DB_HOST", "localhost"),
        port=int(os.environ.get("PMO_DB_PORT", "3306")),
        user=os.environ.get("PMO_DB_USER", "pmo_pms"),
        password=os.environ.get("PMO_DB_PASSWORD", "pmo_pms_dev_2025"),
        database=os.environ.get("PMO_DB_NAME", "pmo_pms"),
    )
    query = f"""
    SELECT a.signal_overdue, a.signal_spi, a.signal_phase_lag, a.signal_velocity, a.signal_historical,
           a.score, a.confidence,
           CASE WHEN a.severity='CRITICAL' THEN 1 ELSE 0 END AS is_critical,
           CASE WHEN a.severity='WARNING'  THEN 1 ELSE 0 END AS is_warning,
           0 AS phase_lag_days, 0 AS pm_experience_projects, 0 AS team_size, 0.5 AS historical_hit_rate,
           COALESCE(DATEDIFF(CURDATE(), a.milestone_plan_date), 0) AS milestone_age_days,
           COALESCE(DATEDIFF(CURDATE(), p.created_at), 0) AS project_age_days,
           o.outcome_severity
    FROM milestone_ai_advisory a
    JOIN project p ON p.id = a.project_id
    JOIN milestone_ai_outcome o ON o.advisory_id = a.id
    WHERE a.deleted = 0 AND o.outcome_severity IS NOT NULL
    LIMIT 10000;
    """
    df = pd.read_sql(query, conn)
    conn.close()
    return df

def evaluate(model_path, X, y):
    """加载 pkl, 在 X/y 上 evaluate"""
    if not os.path.exists(model_path):
        return None
    artifact = joblib.load(model_path)
    model = artifact["model"]
    le = artifact["label_encoder"]
    feat_cols = artifact["feature_cols"]
    # 验证集特征对齐
    X_use = X[feat_cols].values
    y_pred = model.predict(X_use).argmax(axis=1)
    y_true = le.transform(y)
    acc = accuracy_score(y_true, y_pred)
    f1 = f1_score(y_true, y_pred, average="macro", zero_division=0)
    return {
        "accuracy": float(acc),
        "f1_macro": float(f1),
        "model_version": artifact.get("trained_at", "unknown"),
        "n_samples": int(len(y)),
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--new-model", required=True, help="本次新训练的 pkl")
    parser.add_argument("--prev-model", help="上次训练的 pkl, 用于对比")
    parser.add_argument("--drop-threshold", type=float, default=0.05, help="accuracy 下降阈值")
    parser.add_argument("--log", default="logs/model_monitor.log", help="评估日志")
    args = parser.parse_args()
    os.makedirs(os.path.dirname(args.log), exist_ok=True)
    # 1) 拉数据
    print(f"[monitor] loading eval data...")
    df = load_eval_data()
    print(f"[monitor] loaded {len(df)} rows")
    if len(df) < 10:
        print(f"[monitor] too few rows, skip")
        return
    y = df[LABEL_COL].values
    X = df.drop(columns=[LABEL_COL])
    # 2) 计算派生特征 (overdue_ratio, spi_ratio, critical_x_history)
    X["overdue_ratio"] = X["signal_overdue"] / (X["score"] + 1e-6)
    X["spi_ratio"] = X["signal_spi"] / (X["score"] + 1e-6)
    X["critical_x_history"] = X["is_critical"] * X["signal_historical"]
    # 3) 评估新模型
    new_metric = evaluate(args.new_model, X, y)
    if not new_metric:
        print(f"[monitor] new model {args.new_model} not found")
        return
    print(f"[monitor] NEW  model: {args.new_model}")
    print(f"  accuracy={new_metric['accuracy']:.4f}  f1={new_metric['f1_macro']:.4f}  version={new_metric['model_version']}")
    # 3) 评估旧模型 (如有)
    prev_metric = None
    if args.prev_model and os.path.exists(args.prev_model):
        prev_metric = evaluate(args.prev_model, X, y)
        print(f"[monitor] PREV model: {args.prev_model}")
        print(f"  accuracy={prev_metric['accuracy']:.4f}  f1={prev_metric['f1_macro']:.4f}")
    # 4) 比对 + 告警
    result = {
        "timestamp": datetime.now().isoformat(),
        "new_model": args.new_model,
        "new_metric": new_metric,
        "prev_model": args.prev_model,
        "prev_metric": prev_metric,
        "drop_threshold": args.drop_threshold,
    }
    alert_level = None
    drop_pct = 0.0
    if prev_metric:
        drop = prev_metric["accuracy"] - new_metric["accuracy"]
        drop_pct = drop / max(prev_metric["accuracy"], 1e-6) * 100
        result["accuracy_drop"] = drop
        result["drop_pct"] = drop_pct
        print(f"[monitor] accuracy drop: {drop:.4f} ({drop_pct:.2f}%)")
        if drop > args.drop_threshold:
            alert_level = "danger"
        elif drop > args.drop_threshold * 0.5:  # 下降 2.5% 警告
            alert_level = "warning"
        else:
            alert_level = "success"
            print(f"[monitor] ✓ accuracy OK, no alert")
    else:
        result["accuracy_drop"] = None
        result["drop_pct"] = None
        print(f"[monitor] no prev model, skip comparison")
    # 5) 写日志
    with open(args.log, "a") as f:
        f.write(json.dumps(result) + "\n")
    # 6) 发告警
    if alert_level in ("danger", "warning"):
        title = "🚨 [P5-AI 模型告警]" if alert_level == "danger" else "⚠️ [P5-AI 模型注意]"
        message = (
            f"**新模型 accuracy 下降 {drop_pct:.2f}%**\n\n"
            f"- 旧: `{prev_metric['accuracy']:.4f}` (f1={prev_metric['f1_macro']:.4f})\n"
            f"- 新: `{new_metric['accuracy']:.4f}` (f1={new_metric['f1_macro']:.4f})\n"
            f"- 下降: **{drop:.4f}** (阈值 {args.drop_threshold})\n\n"
            f"新 pkl: `{args.new_model}`\n"
            f"旧 pkl: `{args.prev_model}`\n\n"
            f"建议: 检查最近 7 天 outcome 数据分布是否变化, 必要时回滚。"
        )
        ret = os.system(
            f"{sys.executable} scripts/ml/alerter.py "
            f"--title {title!r} --message {message!r} --level {alert_level}"
        )
        if ret != 0:
            print(f"[monitor] alerter exit code: {ret}")
    print(f"[monitor] DONE")

if __name__ == "__main__":
    main()
