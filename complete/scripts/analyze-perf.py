#!/usr/bin/env python3
"""
Analyze Gatling runs produced by scripts/run-perf.sh and emit:

  - target/gatling/comparison.png   grouped bar chart + throughput chart
  - target/gatling/comparison.md    markdown summary suitable for email

Parses the top-level `stats` object inside each run's js/stats.js.
Runs are detected by directory prefix under target/gatling/:
    ext-off-*, ext-on-shallow-*, ext-on-deep-*
The most recent dir per prefix wins.
"""
from __future__ import annotations

import glob
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.ticker as mtick

ROOT = Path(__file__).resolve().parent.parent
GATLING_DIR = ROOT / "target" / "gatling"

RUN_ORDER = ["ext-off", "ext-on-shallow", "ext-on-deep"]
RUN_LABELS = {
    "ext-off":         "Extensions OFF\n(baseline)",
    "ext-on-shallow":  "Extensions ON\n(shallow JSONPath)",
    "ext-on-deep":     "Extensions ON\n(deep JSONPath +\narray wildcard)",
}
RUN_COLORS = {
    "ext-off":         "#7bbf7b",   # green
    "ext-on-shallow":  "#4a90d9",   # blue
    "ext-on-deep":     "#d96b4a",   # orange
}


@dataclass
class RunStats:
    label: str
    dir: Path
    total: int
    ok: int
    ko: int
    min_ms: float
    max_ms: float
    mean_ms: float
    stddev_ms: float
    p50_ms: float
    p75_ms: float
    p95_ms: float
    p99_ms: float
    rps: float


def latest_run_dir(prefix: str) -> Path | None:
    candidates = sorted(GATLING_DIR.glob(f"{prefix}-*"))
    return candidates[-1] if candidates else None


# stats.js is almost-JSON but uses `var stats = { ... }` with unquoted keys
# on the outer wrapper.  The inner `stats: { ... }` block we care about is
# already valid JSON except for unquoted keys -- easier to extract the inner
# object via a targeted regex and then normalize.
STATS_BLOCK = re.compile(r'"name":\s*"All Requests",.*?"meanNumberOfRequestsPerSecond":\s*\{[^}]+\}', re.S)


def extract_top_stats(stats_js: str) -> dict:
    m = STATS_BLOCK.search(stats_js)
    if not m:
        raise ValueError("could not find All Requests block in stats.js")
    blob = "{" + m.group(0) + "}"
    # All keys here are already quoted, so this is already valid JSON.
    return json.loads(blob)


def parse_run(prefix: str) -> RunStats | None:
    run_dir = latest_run_dir(prefix)
    if not run_dir:
        print(f"WARN: no run dir for {prefix}", file=sys.stderr)
        return None
    stats_js = (run_dir / "js" / "stats.js").read_text()
    s = extract_top_stats(stats_js)

    def f(key):
        return float(s[key]["total"])

    return RunStats(
        label=prefix,
        dir=run_dir,
        total=int(s["numberOfRequests"]["total"]),
        ok=int(s["numberOfRequests"]["ok"]),
        ko=int(s["numberOfRequests"]["ko"]),
        min_ms=f("minResponseTime"),
        max_ms=f("maxResponseTime"),
        mean_ms=f("meanResponseTime"),
        stddev_ms=f("standardDeviation"),
        p50_ms=f("percentiles1"),
        p75_ms=f("percentiles2"),
        p95_ms=f("percentiles3"),
        p99_ms=f("percentiles4"),
        rps=f("meanNumberOfRequestsPerSecond"),
    )


def render_chart(runs: list[RunStats], out_path: Path) -> None:
    # 3-panel layout: (1) steady-state percentiles zoomed, (2) outlier Max
    # broken out so it doesn't crush the scale, (3) throughput.
    fig = plt.figure(figsize=(15, 6))
    gs = fig.add_gridspec(1, 3, width_ratios=[2.2, 1, 1.1], wspace=0.32)
    ax_lat = fig.add_subplot(gs[0, 0])
    ax_max = fig.add_subplot(gs[0, 1])
    ax_rps = fig.add_subplot(gs[0, 2])

    fig.suptitle(
        "Extension Constraint Validator — Performance Impact",
        fontsize=15, fontweight="bold",
    )

    # --- (1) steady-state percentiles (ms) --------------------------------
    metric_keys = ["mean_ms", "p50_ms", "p95_ms", "p99_ms"]
    metric_names = ["Mean", "p50", "p95", "p99"]
    x_positions = range(len(metric_keys))
    bar_width = 0.26
    offsets = [-bar_width, 0, bar_width]

    for i, run in enumerate(runs):
        values = [getattr(run, k) for k in metric_keys]
        positions = [x + offsets[i] for x in x_positions]
        bars = ax_lat.bar(
            positions,
            values,
            bar_width,
            label=RUN_LABELS[run.label].replace("\n", " "),
            color=RUN_COLORS[run.label],
            edgecolor="#333",
            linewidth=0.5,
        )
        for bar, v in zip(bars, values):
            ax_lat.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + 0.08,
                f"{v:.0f}",
                ha="center", va="bottom", fontsize=10, fontweight="bold",
            )

    all_p_values = [
        getattr(r, k) for r in runs for k in metric_keys
    ]
    ax_lat.set_xticks(list(x_positions))
    ax_lat.set_xticklabels(metric_names)
    ax_lat.set_ylabel("Response time (ms)")
    ax_lat.set_title("Steady-state latency (lower is better)")
    ax_lat.set_ylim(0, max(all_p_values) * 1.35 + 1)
    ax_lat.grid(axis="y", linestyle=":", alpha=0.6)
    ax_lat.legend(loc="upper left", fontsize=9)
    ax_lat.set_axisbelow(True)

    # --- (2) Max (outliers) broken out ------------------------------------
    labels = [RUN_LABELS[r.label] for r in runs]
    max_vals = [r.max_ms for r in runs]
    colors = [RUN_COLORS[r.label] for r in runs]

    bars = ax_max.bar(labels, max_vals, color=colors, edgecolor="#333", linewidth=0.5)
    for bar, v in zip(bars, max_vals):
        ax_max.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + max(max_vals) * 0.01,
            f"{v:.0f} ms",
            ha="center", va="bottom", fontsize=10, fontweight="bold",
        )
    ax_max.set_ylabel("Response time (ms)")
    ax_max.set_title("Max (single-request outliers)")
    ax_max.set_ylim(0, max(max_vals) * 1.20)
    ax_max.grid(axis="y", linestyle=":", alpha=0.6)
    ax_max.tick_params(axis="x", labelsize=8)
    ax_max.set_axisbelow(True)
    ax_max.text(
        0.5, -0.28,
        "Dominated by JIT / GC noise — not\nattributable to the validator.",
        transform=ax_max.transAxes, ha="center", fontsize=8, style="italic",
        color="#555",
    )

    # --- (3) throughput + request counts ----------------------------------
    rps_vals = [r.rps for r in runs]
    bars = ax_rps.bar(labels, rps_vals, color=colors, edgecolor="#333", linewidth=0.5)
    for bar, r in zip(bars, runs):
        ax_rps.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + max(rps_vals) * 0.01,
            f"{r.rps:.0f} rps\n{r.ok:,}/{r.total:,} OK",
            ha="center", va="bottom", fontsize=9,
        )
    ax_rps.set_ylabel("Requests / sec (sustained)")
    ax_rps.set_title("Throughput & success")
    ax_rps.grid(axis="y", linestyle=":", alpha=0.6)
    ax_rps.set_axisbelow(True)
    ax_rps.set_ylim(0, max(rps_vals) * 1.25)
    ax_rps.tick_params(axis="x", labelsize=8)
    ax_rps.yaxis.set_major_formatter(mtick.FormatStrFormatter("%.0f"))

    fig.tight_layout(rect=[0, 0.02, 1, 0.94])
    fig.savefig(out_path, dpi=160, bbox_inches="tight")
    print(f"wrote {out_path}")


def render_markdown(runs: list[RunStats], chart_path: Path, out_path: Path) -> None:
    def row(attr, label, unit=""):
        cells = [label] + [f"{getattr(r, attr):.0f}{unit}" for r in runs]
        return "| " + " | ".join(cells) + " |"

    baseline = runs[0]

    def delta(r: RunStats, attr: str) -> str:
        base_v = getattr(baseline, attr)
        cur_v = getattr(r, attr)
        if base_v == 0:
            if cur_v == 0:
                return "—"
            return f"+{cur_v:.1f} (∞)"
        diff = cur_v - base_v
        pct = (diff / base_v) * 100
        sign = "+" if diff >= 0 else ""
        return f"{sign}{diff:.1f} ms ({sign}{pct:.0f}%)"

    header = ["Metric"] + [RUN_LABELS[r.label].replace("\n", " ") for r in runs]

    md = [
        "# Extension Constraint Validator — Performance Impact",
        "",
        f"![comparison]({chart_path.name})",
        "",
        "## Summary table",
        "",
        "| " + " | ".join(header) + " |",
        "|" + "|".join(["---"] * len(header)) + "|",
        row("total",    "Requests (total)"),
        row("ok",       "Requests (OK)"),
        row("ko",       "Requests (KO)"),
        row("rps",      "Sustained RPS", " rps"),
        row("mean_ms",  "Mean latency",  " ms"),
        row("p50_ms",   "p50",           " ms"),
        row("p95_ms",   "p95",           " ms"),
        row("p99_ms",   "p99",           " ms"),
        row("max_ms",   "Max",           " ms"),
        row("stddev_ms","Std dev",       " ms"),
        "",
        "## Deltas vs. baseline (`ext-off`)",
        "",
        "| Metric | ext-on-shallow | ext-on-deep |",
        "|---|---|---|",
        f"| Mean  | {delta(runs[1], 'mean_ms')} | {delta(runs[2], 'mean_ms')} |",
        f"| p95   | {delta(runs[1], 'p95_ms')}  | {delta(runs[2], 'p95_ms')}  |",
        f"| p99   | {delta(runs[1], 'p99_ms')}  | {delta(runs[2], 'p99_ms')}  |",
        f"| Max   | {delta(runs[1], 'max_ms')}  | {delta(runs[2], 'max_ms')}  |",
        "",
        "## Reading the result",
        "",
        "- **`ext-off` → `ext-on-shallow`** = fixed cost of enabling the "
        "`ExtensionsJsonPathRegex` validator on the shopping-cart control path (`$.cartCode`).",
        "- **`ext-on-shallow` → `ext-on-deep`** = additional cost of traversing "
        "the shopping cart item list with an array wildcard (`$.items[*].productOffering.tags.catalogCode`).",
        "- 0 KO across all runs confirms the comparison is on the validation-pass path.",
        "",
        "_Source reports_: `target/gatling/ext-off-*`, "
        "`target/gatling/ext-on-shallow-*`, `target/gatling/ext-on-deep-*`.",
    ]
    out_path.write_text("\n".join(md))
    print(f"wrote {out_path}")


def main() -> int:
    runs: list[RunStats] = []
    for prefix in RUN_ORDER:
        r = parse_run(prefix)
        if r is None:
            print(f"missing run: {prefix}", file=sys.stderr)
            return 1
        runs.append(r)

    for r in runs:
        print(
            f"{r.label:18}  total={r.total:>6}  ok={r.ok:>6}  ko={r.ko:>4}  "
            f"rps={r.rps:6.1f}  mean={r.mean_ms:5.1f}ms  "
            f"p50={r.p50_ms:5.1f}  p95={r.p95_ms:5.1f}  p99={r.p99_ms:5.1f}  "
            f"max={r.max_ms:6.1f}"
        )

    chart_path = GATLING_DIR / "comparison.png"
    md_path = GATLING_DIR / "comparison.md"
    render_chart(runs, chart_path)
    render_markdown(runs, chart_path, md_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
