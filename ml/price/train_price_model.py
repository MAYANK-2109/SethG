"""
On-device price model: predicts the ₹/kg buying rate range for an e-waste
category at a location, as quantiles (25th / 50th / 75th percentile).

Training data
  1. data/prices/observations.json — sourced scrap-dealer rates, cleaned with the
     same rules as data/prices/build_rates.py (bare copper, resale prices, unit
     mistakes dropped; per-piece prices converted to ₹/kg)
  2. data/transactions/*.csv (optional) — real final sale prices recorded by the
     app: category, weight_kg, final_price, lat, lon, zone. Weighted higher,
     because they are what was actually paid, not advertised.

Features   category one-hot · zone one-hot · standardized lat/lon
Target     log(₹/kg)  (rates span ₹5–₹500/kg, so errors are relative)
Model      small MLP, pinball (quantile) loss for q = 0.25, 0.5, 0.75

Validation: leave-one-city-out. Each city is held out in turn and predicted from
the others, which is exactly the situation of a user in a city with no data.
Compared against the current lookup (zone median of other cities, else national).

Outputs  ml/price/runs/price_model.tflite and price_model_meta.json
         (copy both into android/app/src/main/assets/)

Run:  ml/.venv/bin/python ml/price/train_price_model.py
"""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import tensorflow as tf

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "data" / "prices"))
import build_rates as br  # noqa: E402  (shared cleaning rules)

RUNS = Path(__file__).parent / "runs"
QUANTILES = [0.1, 0.5, 0.9]
CATEGORIES = br.CATEGORIES
ZONES = ["NORTH", "WEST", "SOUTH", "EAST", "CENTRAL", "NORTHEAST"]
# No dealer prices these separately — sold in the mixed e-waste lot (see build_rates.py)
CATEGORY_ALIAS = {c: "OTHER" for c in br.SAME_AS_OTHER}
TRANSACTION_WEIGHT = 5.0
SEED = 7


# ── Data ──────────────────────────────────────────────────────────────────────

def load_rows():
    cities = {c["name"]: c for c in json.loads((ROOT / "data/prices/cities.json").read_text())}
    rows = []
    for obs in json.loads((ROOT / "data/prices/observations.json").read_text()):
        obs = dict(obs, city=br.CITY_ALIASES.get(obs["city"], obs["city"]))
        if br.excluded(obs) or obs["city"] not in cities or obs["category"] not in CATEGORIES:
            continue
        r = br.per_kg(obs)
        if r is None:
            continue
        c = cities[obs["city"]]
        # min and max both become samples, so the model learns the spread too
        for price in {r[0], r[1]}:
            if price > 0:
                rows.append(dict(category=obs["category"], zone=c["zone"], lat=c["lat"], lon=c["lon"],
                                 city=c["name"], price=price, weight=1.0, source="market"))

    for f in sorted((ROOT / "data/transactions").glob("*.csv")):
        for t in csv.DictReader(f.open()):
            kg, paid = float(t["weight_kg"]), float(t["final_price"])
            if kg > 0 and paid > 0:
                rows.append(dict(category=t["category"], zone=t["zone"], lat=float(t["lat"]), lon=float(t["lon"]),
                                 city=t.get("city", ""), price=paid / kg, weight=TRANSACTION_WEIGHT,
                                 source="transaction"))
    return rows


def featurize(rows, geo):
    X = np.zeros((len(rows), len(CATEGORIES) + len(ZONES) + 2), dtype=np.float32)
    for i, r in enumerate(rows):
        X[i, CATEGORIES.index(CATEGORY_ALIAS.get(r["category"], r["category"]))] = 1
        X[i, len(CATEGORIES) + ZONES.index(r["zone"])] = 1
        X[i, -2] = (r["lat"] - geo["lat_mean"]) / geo["lat_std"]
        X[i, -1] = (r["lon"] - geo["lon_mean"]) / geo["lon_std"]
    return X


def geo_stats(rows):
    lats, lons = [r["lat"] for r in rows], [r["lon"] for r in rows]
    return dict(lat_mean=float(np.mean(lats)), lat_std=float(np.std(lats)) or 1.0,
                lon_mean=float(np.mean(lons)), lon_std=float(np.std(lons)) or 1.0)


# ── Model ─────────────────────────────────────────────────────────────────────

def pinball(y_true, y_pred):
    q = tf.constant(QUANTILES)
    err = y_true - y_pred                      # y_true broadcast over the 3 quantiles
    return tf.reduce_mean(tf.maximum(q * err, (q - 1) * err), axis=-1)


def build_model(n_features):
    reg = tf.keras.regularizers.l2(1e-3)
    inputs = tf.keras.Input((n_features,))
    x = tf.keras.layers.Dense(16, activation="relu", kernel_regularizer=reg)(inputs)
    outputs = tf.keras.layers.Dense(len(QUANTILES), kernel_regularizer=reg)(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(0.01), loss=pinball)
    return model


def fit(rows, geo):
    tf.keras.utils.set_random_seed(SEED)
    X = featurize(rows, geo)
    y = np.log([r["price"] for r in rows]).astype(np.float32)[:, None]
    w = np.array([r["weight"] for r in rows], dtype=np.float32)
    model = build_model(X.shape[1])
    model.fit(X, y, sample_weight=w, epochs=600, batch_size=len(rows), verbose=0)
    return model


def predict(model, rows, geo):
    q = np.sort(model.predict(featurize(rows, geo), verbose=0), axis=1)   # keep q25 ≤ q50 ≤ q75
    return np.exp(q)


# ── Baseline = today's lookup table for a city with no data ───────────────────

def lookup_baseline(train_rows, row):
    by_city = defaultdict(list)
    for r in train_rows:
        if CATEGORY_ALIAS.get(r["category"], r["category"]) == CATEGORY_ALIAS.get(row["category"], row["category"]):
            by_city[(r["city"], r["zone"])].append(r["price"])
    city_medians = {k: statistics.median(v) for k, v in by_city.items()}
    zone = [m for (c, z), m in city_medians.items() if z == row["zone"]]
    pool = zone or list(city_medians.values())
    if not pool:
        return None
    mid = statistics.median(pool)
    return mid * 0.85, mid, mid * 1.15          # same ±15% band the app shows


# ── Leave-one-city-out evaluation ─────────────────────────────────────────────

def evaluate(rows):
    cities = sorted({r["city"] for r in rows if r["source"] == "market"})
    ml_err, base_err, ml_in, base_in, n = [], [], 0, 0, 0
    for city in cities:
        train = [r for r in rows if r["city"] != city]
        test = [r for r in rows if r["city"] == city]
        geo = geo_stats(train)
        preds = predict(fit(train, geo), test, geo)
        for r, (lo, mid, hi) in zip(test, preds):
            base = lookup_baseline(train, r)
            if base is None:
                continue
            n += 1
            ml_err.append(abs(np.log(mid) - np.log(r["price"])))
            base_err.append(abs(np.log(base[1]) - np.log(r["price"])))
            ml_in += lo <= r["price"] <= hi
            base_in += base[0] <= r["price"] <= base[2]
        print(f"  held out {city:<12} {len(test):>3} rates")

    typical = lambda errs: np.exp(np.median(errs)) - 1   # median error as a % off
    report = {
        "cities_held_out": len(cities),
        "test_rates": n,
        "ml_typical_error_pct": round(100 * typical(ml_err), 1),
        "lookup_typical_error_pct": round(100 * typical(base_err), 1),
        "ml_range_contains_real_rate_pct": round(100 * ml_in / n, 1),
        "lookup_range_contains_real_rate_pct": round(100 * base_in / n, 1),
    }
    return report


# ── Export ────────────────────────────────────────────────────────────────────

def export(model, geo, rows, report):
    RUNS.mkdir(exist_ok=True)
    tflite = tf.lite.TFLiteConverter.from_keras_model(model).convert()
    (RUNS / "price_model.tflite").write_bytes(tflite)
    meta = {
        "version": 1,
        "trained_on": "2026-09-26",
        "features": {"categories": CATEGORIES, "zones": ZONES, "category_alias": CATEGORY_ALIAS, **geo},
        "output": "log(INR per kg) at quantiles " + ", ".join(map(str, QUANTILES)),
        "training_rows": {"market": sum(r["source"] == "market" for r in rows),
                          "transaction": sum(r["source"] == "transaction" for r in rows)},
        "categories_with_data": sorted({CATEGORY_ALIAS.get(r["category"], r["category"]) for r in rows}),
        "validation_leave_one_city_out": report,
    }
    (RUNS / "price_model_meta.json").write_text(json.dumps(meta, indent=1))
    print(f"\nSaved ml/price/runs/price_model.tflite ({len(tflite) / 1024:.1f} KB) + price_model_meta.json")


def main():
    rows = load_rows()
    print(f"training rows: {len(rows)} "
          f"(market {sum(r['source'] == 'market' for r in rows)}, "
          f"transactions {sum(r['source'] == 'transaction' for r in rows)})")

    print("\nLeave-one-city-out validation:")
    report = evaluate(rows)
    print(json.dumps(report, indent=1))

    geo = geo_stats(rows)
    model = fit(rows, geo)

    probe = [dict(category=c, zone=z, lat=lat, lon=lon) for c in ["CABLE", "BATTERY", "PCB", "OTHER"]
             for (z, lat, lon) in [("CENTRAL", 21.25, 81.63), ("WEST", 19.08, 72.88), ("EAST", 25.59, 85.14)]]
    print("\nSample predictions (₹/kg q25 / q50 / q75) — Raipur, Mumbai, Patna (no Patna data):")
    for p, (lo, mid, hi) in zip(probe, predict(model, probe, geo)):
        print(f"  {p['category']:<8} {p['zone']:<8} {lo:7.1f} {mid:7.1f} {hi:7.1f}")

    export(model, geo, rows, report)


if __name__ == "__main__":
    main()
