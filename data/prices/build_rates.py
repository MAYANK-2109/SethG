"""
Builds android/app/src/main/assets/ewaste_rates.json (₹/kg, city → zone → national)
from sourced rate observations.

Inputs (this folder):
  cities.json         city list with zone + coordinates (used for offline nearest-city lookup)
  observations.json   one row per rate seen on a real source:
                      {city, state, zone, category, item_as_listed, min, max, unit,
                       source_url, source_date, notes}

Rules:
  • rows matching EXCLUDE are dropped (bare copper, resale prices of working
    devices, unit mistakes flagged during research)
  • per-piece prices are converted to ₹/kg with the typical item weights in
    KG_PER_PIECE (documented assumption); other per-piece rows are skipped
  • city rate     = median of that city's observations for the category
  • zone rate     = median across the zone's city rates
  • national rate = median across all city rates; "India (national)" rows are
    used only for categories no city has
  • CHARGER / SWITCH: no dealer prices them separately — they are sold in the
    general e-waste lot, so they take the OTHER (mixed e-waste) rate
  • a single price is widened to a ±15% range: a photo tells category, not grade
"""
import json
import statistics
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE.parent.parent / "android" / "app" / "src" / "main" / "assets" / "ewaste_rates.json"

CATEGORIES = ["CABLE", "CHARGER", "PCB", "MOBILE", "BATTERY", "MOTOR",
              "SWITCH", "LCD", "CRT", "PLASTIC", "OTHER"]

CITY_ALIASES = {"Delhi NCR": "Delhi", "Ghaziabad/Noida": "Noida"}
NATIONAL = "India (national)"
SAME_AS_OTHER = ["CHARGER", "SWITCH"]

# (category, substring of item_as_listed or source host, reason) — case-insensitive
EXCLUDE = [
    ("CABLE", "bare", "bare copper, not insulated cable"),
    ("CABLE", "bright", "bare copper, not insulated cable"),
    ("CABLE", "copper (wire/mix)", "includes bare copper"),
    ("CABLE", "ecorecyle.in", "page labels kg prices as /piece"),
    ("MOBILE", "kabadiking.in", "resale range for working phones, not scrap"),
    ("LCD", "kabadiking.in", "resale range for working TVs, not scrap"),
    ("OTHER", "thekabadiwala.com/scrap-rates/bhopal", "unit shown as /pcs, likely /kg typo"),
]

# Typical weight of one item (kg), to turn per-piece prices into ₹/kg.
# First keyword match on item_as_listed wins; "" is the category default.
KG_PER_PIECE = {
    "MOBILE": [("keypad", 0.09), ("feature", 0.09), ("tab", 0.25), ("", 0.18)],
    "CRT":    [("monitor/tv", 16), ("tv/monitor", 16), ("monitor", 12), ("", 20)],   # 17" monitor, 21" TV
    "LCD":    [("monitor/tv", 4.5), ("tv/monitor", 4.5), ("monitor", 3.5), ("", 6)], # 19" monitor, 32" TV
}


def excluded(obs):
    hay = (obs["item_as_listed"] + " " + obs["source_url"]).lower()
    for cat, needle, reason in EXCLUDE:
        if obs["category"] == cat and needle in hay:
            return reason
    return None


def per_kg(obs):
    lo, hi = float(obs["min"]), float(obs["max"])
    if obs["unit"] == "per_kg":
        return lo, hi
    item = obs["item_as_listed"].lower()
    for keyword, kg in KG_PER_PIECE.get(obs["category"], []):
        if keyword in item:
            return lo / kg, hi / kg
    return None


def median_range(ranges):
    low = statistics.median(r[0] for r in ranges)
    high = statistics.median(r[1] for r in ranges)
    if high < low * 1.3:          # (near-)single price → honest ±15% band
        mid = (low + high) / 2
        low, high = mid * 0.85, mid * 1.15
    return {"low": round(low, 1), "high": round(high, 1)}


def main():
    cities = json.loads((HERE / "cities.json").read_text())
    observations = json.loads((HERE / "observations.json").read_text())
    known = {c["name"] for c in cities}

    by_city = defaultdict(lambda: defaultdict(list))
    national_only = defaultdict(list)
    skipped = []
    for obs in observations:
        obs = dict(obs, city=CITY_ALIASES.get(obs["city"], obs["city"]))
        reason = excluded(obs)
        if reason:
            skipped.append((obs["city"], obs["category"], obs["item_as_listed"], reason))
            continue
        if obs["category"] not in CATEGORIES:
            skipped.append((obs["city"], obs["category"], "unknown category"))
            continue
        if obs["city"] == NATIONAL:
            r = per_kg(obs)
            if r:
                national_only[obs["category"]].append(r)
            continue
        if obs["city"] not in known:
            skipped.append((obs["city"], obs["category"], "city not in cities.json"))
            continue
        r = per_kg(obs)
        if r is None:
            skipped.append((obs["city"], obs["category"], f"unit {obs['unit']} not convertible"))
            continue
        by_city[obs["city"]][obs["category"]].append(r)

    zone_of = {c["name"]: c["zone"] for c in cities}
    city_rates = {city: {cat: median_range(rs) for cat, rs in cats.items()} for city, cats in by_city.items()}

    zone_ranges = defaultdict(lambda: defaultdict(list))
    national_ranges = defaultdict(list)
    for city, cats in city_rates.items():
        for cat, r in cats.items():
            zone_ranges[zone_of[city]][cat].append((r["low"], r["high"]))
            national_ranges[cat].append((r["low"], r["high"]))

    national = {cat: median_range(rs) for cat, rs in national_ranges.items()}
    for cat, rs in national_only.items():
        national.setdefault(cat, median_range(rs))

    zones = {z: {cat: median_range(rs) for cat, rs in cats.items()} for z, cats in zone_ranges.items()}
    for level in [*city_rates.values(), *zones.values(), national]:
        for cat in SAME_AS_OTHER:
            if cat not in level and "OTHER" in level:
                level[cat] = dict(level["OTHER"])

    table = {
        "updated": "2026-09-26",   # date the sources were collected (most pages show no date)
        "unit": "INR_per_kg",
        "cities": [dict(c, rates=city_rates.get(c["name"], {})) for c in cities],
        "zones": zones,
        "national": national,
    }
    OUT.write_text(json.dumps(table, ensure_ascii=False, indent=1))

    # ── Report ────────────────────────────────────────────────────────────────
    print(f"wrote {OUT.relative_to(HERE.parent.parent)}")
    print(f"observations used: {sum(len(v) for c in by_city.values() for v in c.values())}, skipped: {len(skipped)}")
    for s in skipped:
        print("  skip", *s)
    print("cities with rates:", ", ".join(f"{c}({len(r)})" for c, r in sorted(city_rates.items())))
    print("\nnational ₹/kg:")
    for cat in CATEGORIES:
        r = table["national"].get(cat)
        shown = "—  NO DATA" if r is None else f"{r['low']:>7} – {r['high']}"
        print(f"  {cat:<8} {shown}")
    print("\nzone coverage:")
    for z in ["NORTH", "WEST", "SOUTH", "EAST", "CENTRAL", "NORTHEAST"]:
        print(f"  {z:<9} {sorted(table['zones'].get(z, {}))}")


if __name__ == "__main__":
    main()
