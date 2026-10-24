"""Raw native samples and nearest-rank statistics, never callback intervals."""
import math


def summarize(values, unit):
    if unit not in ("ns", "bytes", "count", "seconds"):
        raise ValueError("unsupported measurement unit")
    values = sorted(values)
    if not values or any(type(value) not in (int, float) or not math.isfinite(value) or value < 0 for value in values):
        raise ValueError("empty, negative or nonfinite measurements")
    return {"count": len(values), "unit": unit, **{f"p{percent}": values[math.ceil(len(values) * percent / 100) - 1] for percent in (50, 95, 99)}, "max": values[-1]}


def incremental(candidate, control, unit):
    # Independent processes have independent clocks. Compare distributions only.
    a, b = summarize(candidate, unit), summarize(control, unit)
    return {"p95": max(0, a["p95"] - b["p95"]), "max": max(0, a["max"] - b["max"]), "unit": unit}
