"""
FastAPI microservice that serves the claim fraud/risk-scoring model.

Spring Boot's ClaimService calls POST /predict with raw claim/policy/customer
data (no feature engineering needed on the Java side); this service computes
derived features, runs the model, and returns a risk score + explanation.

Run with:
    uvicorn service.main:app --host 0.0.0.0 --port 8000 --reload
(run from the insurance-ai/ directory so the relative model paths resolve)
"""
import json
from datetime import date
from pathlib import Path
from typing import Optional

import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = BASE_DIR / "model" / "fraud_model.joblib"
MANIFEST_PATH = BASE_DIR / "model" / "manifest.json"

app = FastAPI(title="Insurance Claim Risk Scoring Service")

model = joblib.load(MODEL_PATH)
with open(MANIFEST_PATH) as f:
    manifest = json.load(f)

NUMERIC_FEATURES = manifest["numeric_features"]
FEATURE_IMPORTANCES = manifest["feature_importances"]


class ClaimRiskRequest(BaseModel):
    claim_amount: float
    claim_date: date
    description: str = ""

    policy_start: date
    policy_end: date
    sum_assured: float
    annual_premium: float
    policy_risk_factor: float = 1.0
    policy_type: str = "Term Life"

    customer_dob: Optional[date] = None

    # History: how many claims already exist on this policy, and when the
    # most recent one was filed (Java computes this from the Claim table)
    num_prior_claims: int = 0
    days_since_last_claim: Optional[int] = None


class RiskFactor(BaseModel):
    feature: str
    detail: str


class ClaimRiskResponse(BaseModel):
    risk_score: float          # 0.0 - 1.0 probability of fraud
    risk_level: str            # Low / Medium / High
    top_factors: list[RiskFactor]


SUSPICIOUS_PHRASES = [
    "total loss", "lost everything", "no witnesses", "cash only",
    "urgent please approve", "no receipts available",
]


def compute_age(dob: Optional[date], on: date) -> int:
    if dob is None:
        return 35
    years = on.year - dob.year - ((on.month, on.day) < (dob.month, dob.day))
    return max(years, 0)


def build_features(req: ClaimRiskRequest) -> pd.DataFrame:
    days_since_start = (req.claim_date - req.policy_start).days
    term_days = max((req.policy_end - req.policy_start).days, 1)
    pct_elapsed = days_since_start / term_days
    claim_to_sum_ratio = req.claim_amount / req.sum_assured if req.sum_assured > 0 else 0
    desc_lower = req.description.lower()
    has_suspicious = int(any(p in desc_lower for p in SUSPICIOUS_PHRASES))
    days_since_last = req.days_since_last_claim if req.days_since_last_claim is not None else 999

    row = {
        "claim_amount": req.claim_amount,
        "sum_assured": req.sum_assured,
        "annual_premium": req.annual_premium,
        "customer_age": compute_age(req.customer_dob, req.claim_date),
        "days_since_policy_start": days_since_start,
        "policy_term_days": term_days,
        "pct_term_elapsed": pct_elapsed,
        "num_prior_claims": req.num_prior_claims,
        "days_since_last_claim": min(days_since_last, 999),
        "policy_risk_factor": req.policy_risk_factor,
        "claim_to_sum_ratio": claim_to_sum_ratio,
        "description_length": len(req.description),
        "has_suspicious_phrase": has_suspicious,
        "policy_type": req.policy_type,
    }
    return pd.DataFrame([row])


def explain(features: pd.DataFrame) -> list[RiskFactor]:
    """Simple, honest heuristic explanation: flag the features whose values
    look unusual AND that the model considers important -- not a full SHAP
    computation, but transparent and good enough for an admin-facing reason list."""
    row = features.iloc[0]
    reasons = []

    if row["claim_to_sum_ratio"] > 0.6:
        reasons.append(RiskFactor(
            feature="claim_to_sum_ratio",
            detail=f"Claim is {row['claim_to_sum_ratio']*100:.0f}% of the policy's sum assured (high)"
        ))
    if row["days_since_policy_start"] < 30:
        reasons.append(RiskFactor(
            feature="days_since_policy_start",
            detail=f"Filed only {int(row['days_since_policy_start'])} days after policy start"
        ))
    if row["num_prior_claims"] >= 2:
        reasons.append(RiskFactor(
            feature="num_prior_claims",
            detail=f"{int(row['num_prior_claims'])} prior claims already exist on this policy"
        ))
    if row["days_since_last_claim"] < 15:
        reasons.append(RiskFactor(
            feature="days_since_last_claim",
            detail=f"Only {int(row['days_since_last_claim'])} days since the last claim on this policy"
        ))
    if row["has_suspicious_phrase"] == 1:
        reasons.append(RiskFactor(
            feature="description",
            detail="Claim description contains language flagged as commonly used in suspicious claims"
        ))

    if not reasons:
        reasons.append(RiskFactor(feature="overall", detail="No strong individual risk indicators detected"))

    # Order by model's global importance so the most model-relevant reason appears first
    reasons.sort(key=lambda r: -FEATURE_IMPORTANCES.get(r.feature, 0))
    return reasons[:4]


def risk_level(score: float) -> str:
    if score >= 0.6:
        return "High"
    if score >= 0.3:
        return "Medium"
    return "Low"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=ClaimRiskResponse)
def predict(req: ClaimRiskRequest):
    features = build_features(req)
    X = features[NUMERIC_FEATURES + ["policy_type"]]
    proba = model.predict_proba(X)[0, 1]
    return ClaimRiskResponse(
        risk_score=round(float(proba), 4),
        risk_level=risk_level(proba),
        top_factors=explain(features),
    )
