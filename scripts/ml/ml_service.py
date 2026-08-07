#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P5-ML 推理服务 (FastAPI)
    给 Java MlPredictor 调用的 HTTP 端点。

启动:
  pip3 install fastapi uvicorn joblib lightgbm
  python3 ml_service.py --model models/milestone_lgbm_v1.pkl --port 8000

端点:
  POST /predict
    body: {"signal_overdue":50, "signal_spi":30, ...}
    resp: {"severity":"WARNING", "confidence":0.78, "proba":{...}, "model_version":"..."}

  GET /health
    resp: {"status":"ok", "model_loaded":true, "model_version":"..."}
"""
import argparse
import json
import sys
from pathlib import Path

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="P5-ML Service", version="1.0")

# ============================================================
# 全局 (启动时 load)
# ============================================================
ARTIFACT = None


class PredictRequest(BaseModel):
    signal_overdue: float = 0
    signal_spi: float = 0
    signal_phase_lag: float = 0
    signal_velocity: float = 0
    signal_historical: float = 0
    score: float = 0
    confidence: float = 0
    is_critical: float = 0
    is_warning: float = 0
    phase_lag_days: float = 0
    project_age_days: float = 0
    milestone_age_days: float = 0
    pm_experience_projects: float = 0
    team_size: float = 0
    historical_hit_rate: float = 0


def engineer(d: dict) -> dict:
    """派生特征 (必须与训练脚本完全一致)"""
    d["overdue_ratio"] = d["signal_overdue"] / (d["score"] + 1e-6)
    d["spi_ratio"] = d["signal_spi"] / (d["score"] + 1e-6)
    d["critical_x_history"] = d["is_critical"] * d["signal_historical"]
    return d


@app.post("/predict")
def predict(req: PredictRequest):
    if ARTIFACT is None:
        raise HTTPException(503, "model not loaded")
    model = ARTIFACT["model"]
    le = ARTIFACT["label_encoder"]
    feat_cols = ARTIFACT["feature_cols"]

    d = engineer(req.dict())
    X = np.array([[d.get(c, 0) for c in feat_cols]])
    proba = model.predict(X)[0]
    idx = int(proba.argmax())
    label = le.inverse_transform([idx])[0]
    return {
        "severity": label,
        "confidence": float(proba[idx]),
        "proba": {cls: float(p) for cls, p in zip(le.classes_, proba)},
        "model_version": ARTIFACT.get("trained_at", "unknown"),
    }


@app.get("/health")
def health():
    return {
        "status": "ok" if ARTIFACT else "loading",
        "model_loaded": ARTIFACT is not None,
        "model_version": ARTIFACT.get("trained_at") if ARTIFACT else None,
    }


def main():
    global ARTIFACT
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", "-m", required=True, help="joblib pkl 路径")
    parser.add_argument("--port", "-p", type=int, default=8000)
    parser.add_argument("--host", default="0.0.0.0")
    args = parser.parse_args()

    p = Path(args.model)
    if not p.exists():
        print(f"ERROR: model file not found: {p}", file=sys.stderr)
        sys.exit(1)
    print(f"[ML] loading model from {p}")
    ARTIFACT = joblib.load(p)
    print(f"[ML] model loaded. version={ARTIFACT.get('trained_at')}")
    print(f"[ML] feature_cols: {ARTIFACT['feature_cols']}")
    print(f"[ML] classes: {list(ARTIFACT['label_encoder'].classes_)}")
    print(f"[ML] starting server on {args.host}:{args.port}")
    import uvicorn
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
