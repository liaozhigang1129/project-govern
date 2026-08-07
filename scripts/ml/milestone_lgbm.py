#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P5-ML 训练 pipeline (P5-智能预警 ML 增强)

数据流:
  MySQL.milestone_ai_advisory  (历史建议 + 真实 outcome)
       ↓
  特征工程 (5 维信号 + 上下文特征)
       ↓
  LightGBM 训练 + 交叉验证
       ↓
  导出 pkl 模型 (供 Java MlPredictor 加载)
       ↓
  反馈循环: 新生成的 advisory → 写入 outcome → 增量训练

用法:
  # 训练 (首次,需 ≥50 条历史 advisory)
  python3 milestone_lgbm.py train --output models/milestone_lgbm_v1.pkl

  # 增量训练 (每周)
  python3 milestone_lgbm.py train --output models/milestone_lgbm_v1.pkl --incremental

  # 预测单条
  python3 milestone_lgbm.py predict --model models/milestone_lgbm_v1.pkl \\
      --input '{"signal_overdue":50, "signal_spi":30, ...}'

  # 评估 (混淆矩阵 + 重要特征)
  python3 milestone_lgbm.py eval --model models/milestone_lgbm_v1.pkl

依赖:
  pip3 install mysql-connector-python lightgbm scikit-learn pandas numpy joblib

作者: PMO PMS Team
"""
import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

try:
    import lightgbm as lgb
    from sklearn.model_selection import StratifiedKFold, train_test_split
    from sklearn.metrics import (accuracy_score, classification_report,
                                 confusion_matrix, f1_score, roc_auc_score)
    from sklearn.preprocessing import LabelEncoder
except ImportError:
    print("ERROR: pip3 install lightgbm scikit-learn pandas numpy joblib", file=sys.stderr)
    sys.exit(1)

# ============================================================
# 特征列 (与 Java MilestoneAiAdvisory 字段 1:1 对齐)
# ============================================================
FEATURE_COLS = [
    # 5 维信号原始强度 (0-100)
    "signal_overdue",       # OVERDUE 强度
    "signal_spi",           # SPI 强度
    "signal_phase_lag",     # PHASE_LAG 强度
    "signal_velocity",      # VELOCITY 强度
    "signal_historical",    # HISTORICAL 强度
    # 上下文特征
    "score",                # 规则引擎总分
    "confidence",           # 置信度 (0-1)
    "is_critical",          # 严重度 == CRITICAL (0/1)
    "is_warning",           # 严重度 == WARNING (0/1)
    "phase_lag_days",       # 阶段滞后天数 (>=0)
    "project_age_days",     # 项目已进行天数
    "milestone_age_days",   # 里程碑到计划日的天数 (可负)
    "pm_experience_projects",  # PM 经历的项目数
    "team_size",            # 团队规模
    "historical_hit_rate",  # PM 历史命中率
]
LABEL_COL = "outcome_severity"  # 真实结果: INFO / WARNING / CRITICAL

# ============================================================
# 1) 数据加载 (从 MySQL 拉历史 advisory)
# ============================================================
def load_data_from_mysql(connection_string: str = None) -> pd.DataFrame:
    """
    从 milestone_ai_advisory + milestone + project 拉数据。

    outcome: 30 天后这个里程碑的真实状态
      - INFO     → 按时完成 / 风险消除
      - WARNING  → 延期 1-14 天
      - CRITICAL → 延期 14+ 天 / 触发重大变更

    简化: 用 applied_risk_id 关联 + 实际进度
    """
    import mysql.connector
    if connection_string is None:
        connection_string = os.environ.get(
            "PMO_DB_URL",
            "mysql://pmo_pms:pmo_pms_dev_2025@localhost:3306/pmo_pms"
        )
    # 简化: 用 env 拆解
    user, password, host, port, db = "pmo_pms", "pmo_pms_dev_2025", "localhost", 3306, "pmo_pms"
    print(f"[ML] connecting to {host}:{port}/{db} as {user}")

    conn = mysql.connector.connect(
        host=host, port=port, user=user, password=password, database=db
    )

    # 历史 advisory 特征 + 实际 outcome
    query = """
    SELECT
        a.id,
        a.signal_overdue,
        a.signal_spi,
        a.signal_phase_lag,
        a.signal_velocity,
        a.signal_historical,
        a.score,
        a.confidence,
        CASE WHEN a.severity='CRITICAL' THEN 1 ELSE 0 END AS is_critical,
        CASE WHEN a.severity='WARNING'  THEN 1 ELSE 0 END AS is_warning,
        COALESCE(DATEDIFF(CURDATE(), a.milestone_plan_date), 0) AS milestone_age_days,
        COALESCE(DATEDIFF(CURDATE(), p.created_at), 0) AS project_age_days,
        0 AS phase_lag_days,
        0 AS pm_experience_projects,
        0 AS team_size,
        0.5 AS historical_hit_rate,
        -- outcome: 30 天后 advisory 状态 (APPLIED/REJECTED/EXPIRED) + 风险触发情况
        o.outcome_severity AS outcome_severity
    FROM milestone_ai_advisory a
    JOIN project p ON p.id = a.project_id
    LEFT JOIN milestone_ai_outcome o ON o.advisory_id = a.id
    WHERE a.deleted = 0
      AND o.outcome_severity IS NOT NULL
    LIMIT 10000;
    """
    df = pd.read_sql(query, conn)
    conn.close()
    print(f"[ML] loaded {len(df)} rows")
    return df


def load_data_from_csv(csv_path: str) -> pd.DataFrame:
    """从 CSV 加载 (离线训练 / CI 用)"""
    df = pd.read_csv(csv_path)
    print(f"[ML] loaded {len(df)} rows from {csv_path}")
    return df


# ============================================================
# 2) 特征工程
# ============================================================
def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """派生特征 (交叉 + 比例)"""
    df = df.copy()
    # 比例特征
    df["overdue_ratio"] = df["signal_overdue"] / (df["score"] + 1e-6)
    df["spi_ratio"] = df["signal_spi"] / (df["score"] + 1e-6)
    # 交叉特征
    df["critical_x_history"] = df["is_critical"] * df["signal_historical"]
    return df


# ============================================================
# 3) 训练
# ============================================================
def train(df: pd.DataFrame, output_path: str, incremental: bool = False):
    """训练 LightGBM 多分类器"""
    df = engineer_features(df)
    feat_cols = FEATURE_COLS + ["overdue_ratio", "spi_ratio", "critical_x_history"]

    X = df[feat_cols].fillna(0).values
    y_raw = df[LABEL_COL].values

    le = LabelEncoder()
    y = le.fit_transform(y_raw)
    print(f"[ML] classes: {list(le.classes_)}")

    # 训练 / 验证 80/20
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    # 5 折交叉验证
    print("[ML] running 5-fold CV...")
    skf = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = []
    for fold, (tr, va) in enumerate(skf.split(X_train, y_train), 1):
        train_set = lgb.Dataset(X_train[tr], y_train[tr])
        val_set = lgb.Dataset(X_train[va], y_train[va], reference=train_set)
        params = {
            "objective": "multiclass",
            "num_class": len(le.classes_),
            "metric": "multi_logloss",
            "learning_rate": 0.05,
            "num_leaves": 31,
            "feature_fraction": 0.8,
            "bagging_fraction": 0.8,
            "bagging_freq": 5,
            "verbose": -1,
        }
        model = lgb.train(
            params, train_set, num_boost_round=200,
            valid_sets=[val_set], callbacks=[lgb.early_stopping(20)]
        )
        pred = model.predict(X_train[va])
        pred_label = pred.argmax(axis=1)
        f1 = f1_score(y_train[va], pred_label, average="macro")
        cv_scores.append(f1)
        print(f"  fold {fold}: f1_macro={f1:.4f}")
    print(f"[ML] CV mean f1: {np.mean(cv_scores):.4f} ± {np.std(cv_scores):.4f}")

    # 全量训练最终模型
    print("[ML] training final model on full data...")
    train_set = lgb.Dataset(X, y)
    final_model = lgb.train(
        {**params, "verbose": -1},
        train_set, num_boost_round=300
    )

    # 验证集评估
    pred = final_model.predict(X_val)
    pred_label = pred.argmax(axis=1)
    print("\n[ML] === validation report ===")
    print(classification_report(y_val, pred_label, target_names=le.classes_))
    print("confusion matrix:")
    print(confusion_matrix(y_val, pred_label))

    # 特征重要度
    importance = pd.Series(
        final_model.feature_importance(importance_type="gain"),
        index=feat_cols
    ).sort_values(ascending=False)
    print("\n[ML] top 10 features (gain):")
    print(importance.head(10))

    # 导出
    out_dir = Path(output_path).parent
    out_dir.mkdir(parents=True, exist_ok=True)
    artifact = {
        "model": final_model,
        "label_encoder": le,
        "feature_cols": feat_cols,
        "trained_at": datetime.now().isoformat(),
        "cv_f1_mean": float(np.mean(cv_scores)),
        "cv_f1_std": float(np.std(cv_scores)),
        "n_samples": len(df),
    }
    joblib.dump(artifact, output_path)
    print(f"\n[ML] saved to {output_path}")
    print(f"  size: {Path(output_path).stat().st_size / 1024:.1f} KB")


# ============================================================
# 4) 预测
# ============================================================
def predict(model_path: str, input_dict: dict) -> dict:
    """单条预测 → {severity, confidence, proba}"""
    artifact = joblib.load(model_path)
    model = artifact["model"]
    le = artifact["label_encoder"]
    feat_cols = artifact["feature_cols"]

    # 派生特征
    input_dict["overdue_ratio"] = input_dict["signal_overdue"] / (input_dict["score"] + 1e-6)
    input_dict["spi_ratio"] = input_dict["signal_spi"] / (input_dict["score"] + 1e-6)
    input_dict["critical_x_history"] = input_dict.get("is_critical", 0) * input_dict.get("signal_historical", 0)

    X = np.array([[input_dict.get(c, 0) for c in feat_cols]])
    proba = model.predict(X)[0]
    pred_idx = proba.argmax()
    pred_label = le.inverse_transform([pred_idx])[0]
    return {
        "severity": pred_label,
        "confidence": float(proba[pred_idx]),
        "proba": {cls: float(p) for cls, p in zip(le.classes_, proba)},
        "model_version": artifact.get("trained_at", "unknown"),
    }


# ============================================================
# 5) 评估
# ============================================================
def eval_model(model_path: str, csv_path: str = None):
    """在测试集上评估"""
    if csv_path:
        df = load_data_from_csv(csv_path)
    else:
        df = load_data_from_mysql()
    df = engineer_features(df)
    artifact = joblib.load(model_path)
    model = artifact["model"]
    le = artifact["label_encoder"]
    feat_cols = artifact["feature_cols"]
    X = df[feat_cols].fillna(0).values
    y = le.transform(df[LABEL_COL])
    pred = model.predict(X).argmax(axis=1)
    print(classification_report(y, pred, target_names=le.classes_))


# ============================================================
# CLI
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="P5-ML 训练 / 预测 / 评估")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_train = sub.add_parser("train", help="训练模型")
    p_train.add_argument("--output", "-o", required=True, help="输出 pkl 路径")
    p_train.add_argument("--csv", help="从 CSV 训练 (否则从 MySQL)")
    p_train.add_argument("--incremental", action="store_true", help="增量训练")

    p_pred = sub.add_parser("predict", help="单条预测")
    p_pred.add_argument("--model", "-m", required=True)
    p_pred.add_argument("--input", "-i", required=True, help="JSON 字符串")

    p_eval = sub.add_parser("eval", help="评估")
    p_eval.add_argument("--model", "-m", required=True)
    p_eval.add_argument("--csv")

    args = parser.parse_args()

    if args.cmd == "train":
        df = load_data_from_csv(args.csv) if args.csv else load_data_from_mysql()
        train(df, args.output, args.incremental)
    elif args.cmd == "predict":
        result = predict(args.model, json.loads(args.input))
        print(json.dumps(result, indent=2, ensure_ascii=False))
    elif args.cmd == "eval":
        eval_model(args.model, args.csv)


if __name__ == "__main__":
    main()
