"""
Trains a RandomForest classifier to score insurance claims for fraud risk.
Saves the trained model + a manifest (feature order, encoder categories,
feature importances) so the FastAPI service can load and serve it.
"""
import pandas as pd
import numpy as np
import joblib
import json
from pathlib import Path
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import OneHotEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, roc_auc_score, confusion_matrix

BASE_DIR = Path(__file__).resolve().parent
(BASE_DIR / "model").mkdir(exist_ok=True)
df = pd.read_csv(BASE_DIR / "data" / "claims_synthetic.csv")

NUMERIC_FEATURES = [
    "claim_amount", "sum_assured", "annual_premium", "customer_age",
    "days_since_policy_start", "policy_term_days", "pct_term_elapsed",
    "num_prior_claims", "days_since_last_claim", "policy_risk_factor",
    "claim_to_sum_ratio", "description_length", "has_suspicious_phrase",
]
CATEGORICAL_FEATURES = ["policy_type"]
ALL_FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES
TARGET = "is_fraud"

X = df[ALL_FEATURES]
y = df[TARGET]

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

preprocessor = ColumnTransformer(
    transformers=[
        ("num", "passthrough", NUMERIC_FEATURES),
        ("cat", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
    ]
)

clf = RandomForestClassifier(
    n_estimators=300,
    max_depth=8,
    min_samples_leaf=5,
    class_weight="balanced",
    random_state=42,
    n_jobs=-1,
)

pipeline = Pipeline(steps=[("preprocess", preprocessor), ("model", clf)])
pipeline.fit(X_train, y_train)

y_pred = pipeline.predict(X_test)
y_proba = pipeline.predict_proba(X_test)[:, 1]

print("=== Classification report ===")
print(classification_report(y_test, y_pred, digits=3))
print("ROC-AUC:", round(roc_auc_score(y_test, y_proba), 4))
print("Confusion matrix:\n", confusion_matrix(y_test, y_pred))

# Feature importances (mapped back to readable names)
ohe = pipeline.named_steps["preprocess"].named_transformers_["cat"]
cat_names = list(ohe.get_feature_names_out(CATEGORICAL_FEATURES))
feature_names = NUMERIC_FEATURES + cat_names
importances = pipeline.named_steps["model"].feature_importances_
importance_map = sorted(zip(feature_names, importances), key=lambda x: -x[1])

print("\n=== Feature importances ===")
for name, imp in importance_map:
    print(f"{name:30s} {imp:.4f}")

joblib.dump(pipeline, BASE_DIR / "model" / "fraud_model.joblib")

manifest = {
    "numeric_features": NUMERIC_FEATURES,
    "categorical_features": CATEGORICAL_FEATURES,
    "policy_type_categories": sorted(df["policy_type"].unique().tolist()),
    "feature_importances": {name: float(imp) for name, imp in importance_map},
    "test_roc_auc": float(roc_auc_score(y_test, y_proba)),
}
with open(BASE_DIR / "model" / "manifest.json", "w") as f:
    json.dump(manifest, f, indent=2)

print("\nSaved model ->", BASE_DIR / "model" / "fraud_model.joblib")
print("Saved manifest ->", BASE_DIR / "model" / "manifest.json")
