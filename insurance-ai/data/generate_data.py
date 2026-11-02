"""
Generates a synthetic but realistic dataset of insurance claims for training
a fraud/risk-scoring model, modeled on the actual Claim/Policy/PolicyType/Customer
schema in the Insurance Policy Management System.

We don't hand-label with a hard rule -> we assign each claim a *fraud probability*
from known real-world fraud indicators, add noise, then sample a binary outcome.
This keeps the problem genuinely learnable (not memorizable) with realistic
~85-92% model performance, rather than a trivial 100%-accuracy toy dataset.
"""
import numpy as np
import pandas as pd
from datetime import date, timedelta
import random
from pathlib import Path

RNG_SEED = 42
np.random.seed(RNG_SEED)
random.seed(RNG_SEED)

N = 8000

POLICY_TYPES = [
    # name, base_rate, risk_factor
    ("Term Life", 1.0, 1.0),
    ("Health", 1.4, 1.5),
    ("Vehicle", 1.2, 1.3),
    ("Home", 0.9, 1.1),
    ("Travel", 0.6, 0.8),
]

SUSPICIOUS_PHRASES = [
    "total loss", "lost everything", "no witnesses", "cash only",
    "urgent please approve", "no receipts available"
]

VAGUE_DESCRIPTIONS = [
    "damage happened", "item lost", "accident occurred", "please process",
    "as discussed", "claim for loss"
]

DETAILED_DESCRIPTIONS = [
    "Vehicle rear-ended at signal on MG Road, police FIR filed, repair estimate attached",
    "Hospitalized for appendicitis surgery at Apollo Hospital, discharge summary attached",
    "Kitchen fire damaged cabinets and wiring, fire dept report and photos attached",
    "Flight cancelled due to weather, airline confirmation and boarding pass attached",
    "Two-wheeler theft reported to local police station, FIR copy attached",
]


def random_date(start_year=2019, end_year=2025):
    start = date(start_year, 1, 1)
    end = date(end_year, 12, 31)
    delta = (end - start).days
    return start + timedelta(days=random.randint(0, delta))


def make_row(i):
    type_name, base_rate, risk_factor = random.choice(POLICY_TYPES)

    # Customer
    age = int(np.clip(np.random.normal(38, 12), 18, 75))

    # Policy
    policy_start = random_date(2019, 2024)
    term_years = random.choice([1, 1, 1, 2, 3, 5])
    policy_end = policy_start + timedelta(days=365 * term_years)
    sum_assured = float(np.random.choice(
        [50000, 100000, 200000, 300000, 500000, 1000000, 2000000],
        p=[0.1, 0.2, 0.25, 0.2, 0.15, 0.07, 0.03]
    ))
    age_factor = 0.9 if age <= 25 else 1.0 if age <= 40 else 1.25 if age <= 55 else 1.6 if age <= 65 else 2.0
    annual_premium = (sum_assured / 1000) * base_rate * age_factor * risk_factor

    # Claim: must fall within policy period
    max_claim_day_offset = max((policy_end - policy_start).days - 1, 1)
    days_since_policy_start = random.randint(1, max_claim_day_offset)
    claim_date = policy_start + timedelta(days=days_since_policy_start)
    policy_term_days = (policy_end - policy_start).days
    pct_term_elapsed = days_since_policy_start / policy_term_days

    # Claim history on this policy (prior claims before this one)
    num_prior_claims = int(np.random.choice([0, 0, 0, 1, 1, 2, 3, 4], p=[0.45, 0.15, 0.1, 0.1, 0.08, 0.06, 0.04, 0.02]))
    days_since_last_claim = random.randint(3, 400) if num_prior_claims > 0 else 999

    # Claim amount: usually a modest fraction of sum assured, occasionally very high
    ratio_choice = np.random.choice(["low", "mid", "high", "extreme"], p=[0.55, 0.3, 0.1, 0.05])
    if ratio_choice == "low":
        claim_to_sum_ratio = np.random.uniform(0.01, 0.15)
    elif ratio_choice == "mid":
        claim_to_sum_ratio = np.random.uniform(0.15, 0.4)
    elif ratio_choice == "high":
        claim_to_sum_ratio = np.random.uniform(0.4, 0.8)
    else:
        claim_to_sum_ratio = np.random.uniform(0.8, 1.2)
    claim_amount = round(claim_to_sum_ratio * sum_assured, 2)

    # Description
    desc_style = np.random.choice(["detailed", "vague", "suspicious"], p=[0.55, 0.3, 0.15])
    if desc_style == "detailed":
        description = random.choice(DETAILED_DESCRIPTIONS)
    elif desc_style == "vague":
        description = random.choice(VAGUE_DESCRIPTIONS)
    else:
        description = random.choice(SUSPICIOUS_PHRASES) + " - " + random.choice(VAGUE_DESCRIPTIONS)
    description_length = len(description)
    has_suspicious_phrase = int(desc_style == "suspicious")

    # ---- Fraud probability from known real-world indicators (soft, noisy) ----
    p = 0.04  # base rate
    if days_since_policy_start < 30:
        p += 0.25          # claim right after policy starts -> classic red flag
    elif days_since_policy_start < 90:
        p += 0.10
    if claim_to_sum_ratio > 0.8:
        p += 0.35
    elif claim_to_sum_ratio > 0.4:
        p += 0.12
    if num_prior_claims >= 3:
        p += 0.20
    elif num_prior_claims == 2:
        p += 0.08
    if days_since_last_claim < 15:
        p += 0.18
    if has_suspicious_phrase:
        p += 0.15
    if desc_style == "vague":
        p += 0.05
    p += (risk_factor - 1.0) * 0.05   # riskier policy types slightly more prone
    p += np.random.normal(0, 0.06)     # noise so it's not perfectly separable
    p = float(np.clip(p, 0.01, 0.97))

    is_fraud = int(np.random.random() < p)

    return {
        "claim_amount": claim_amount,
        "sum_assured": sum_assured,
        "annual_premium": round(annual_premium, 2),
        "customer_age": age,
        "days_since_policy_start": days_since_policy_start,
        "policy_term_days": policy_term_days,
        "pct_term_elapsed": round(pct_term_elapsed, 4),
        "num_prior_claims": num_prior_claims,
        "days_since_last_claim": min(days_since_last_claim, 999),
        "policy_risk_factor": risk_factor,
        "claim_to_sum_ratio": round(claim_to_sum_ratio, 4),
        "description_length": description_length,
        "has_suspicious_phrase": has_suspicious_phrase,
        "policy_type": type_name,
        "is_fraud": is_fraud,
    }


if __name__ == "__main__":
    rows = [make_row(i) for i in range(N)]
    df = pd.DataFrame(rows)
    out_path = Path(__file__).resolve().parent / "claims_synthetic.csv"
    df.to_csv(out_path, index=False)
    print(df["is_fraud"].value_counts(normalize=True))
    print(df.shape)
    print(df.head())
