# Claim Fraud/Risk Scoring — AI Microservice

A trained RandomForest classifier that scores insurance claims for fraud risk,
served via FastAPI and called by the Spring Boot backend's `ClaimService`.

## What it does

Given a claim + its policy + customer context, it returns:
```json
{
  "risk_score": 0.7763,
  "risk_level": "High",
  "top_factors": [
    { "feature": "claim_to_sum_ratio", "detail": "Claim is 96% of the policy's sum assured (high)" },
    { "feature": "days_since_policy_start", "detail": "Filed only 19 days after policy start" }
  ]
}
```

## Project layout

```
insurance-ai/
├── data/
│   ├── generate_data.py       # builds a realistic synthetic training set
│   └── claims_synthetic.csv   # generated dataset (8000 rows)
├── model/
│   ├── fraud_model.joblib     # trained sklearn Pipeline (preprocessing + RandomForest)
│   └── manifest.json          # feature list + feature importances + test ROC-AUC
├── service/
│   └── main.py                # FastAPI app, exposes POST /predict
├── train_model.py             # trains the model from data/claims_synthetic.csv
└── requirements.txt
```

## How the model was trained

Real historical fraud-labeled claims weren't available yet, so `generate_data.py`
builds a synthetic dataset using the same fields as your actual schema
(Claim, Policy, PolicyType, Customer), and assigns each row a *soft* fraud
probability based on known real-world insurance fraud indicators — claims filed
right after a policy starts, claims close to the full sum assured, rapid repeat
claims on the same policy, and suspicious language in the description — then
adds noise and samples a binary label. This keeps the classification problem
genuinely learnable rather than a trivially perfect toy dataset.

Result on a held-out 20% test set: **ROC-AUC ≈ 0.76**, fraud-class recall ≈ 55%,
which is realistic for a first-pass fraud model — it flags a meaningful chunk of
suspicious claims without pretending to be perfect. (Re-run `train_model.py`
any time to retrain, e.g. once you swap in real claim data.)

## Running it

```bash
cd insurance-ai
pip install -r requirements.txt

# (Optional) regenerate data + retrain
python data/generate_data.py
python train_model.py

# Start the service (defaults to port 8000)
uvicorn service.main:app --host 0.0.0.0 --port 8000
```

Check it's up: `curl http://localhost:8000/health`

## Spring Boot integration

- `application.properties` → `ai.risk-service.url` (default `http://localhost:8000`)
  and `ai.risk-service.enabled` (default `true`)
- `RestClientConfig.java` → RestTemplate bean with short timeouts (2s connect / 3s read)
  so a down AI service never hangs the main app
- `RiskScoringClient.java` → builds the request from raw claim/policy/customer facts
  and calls `POST /predict`; returns `available: false` gracefully if the service
  is unreachable, instead of throwing
- `ClaimService.getRiskScore(claimId)` → looks up claim history (prior claims on
  the policy, days since the last one) from the DB, since only Java has that context
- `ClaimController` → `GET /api/admin/claims/{id}/risk-score`
- Frontend: `js/admin/claims.js` fetches this endpoint when an admin opens a claim
  and renders a colored risk badge + reasons in `pages/admin/claims.html`

## Retraining with real data later

Once you have real claims with actual outcomes (confirmed fraud / clean), swap
`claims_synthetic.csv` for a real export with the same column names and re-run
`train_model.py` — nothing else needs to change, since the FastAPI service and
Spring Boot integration are already wired to that feature schema.
