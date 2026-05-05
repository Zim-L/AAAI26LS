#!/usr/bin/env python3
"""Plot HV trajectories and median-run objective-space archives.

Expected input layout, produced by RandomFasterExperiment.java:
  <result_dir>/<problem>/<algorithm>/hv-<run>.txt
  <result_dir>/<problem>/<algorithm>/FUN<run>.csv
"""
from __future__ import annotations

import argparse
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib import ticker

PROBLEMS = [
    "KP-100", "KP-200", "KP-500",
    "NK-100-10", "NK-200-10", "NK-500-10",
    "TSP-100", "TSP-200", "TSP-500",
    "QAP-100", "QAP-200", "QAP-500",
]
ALGORITHMS = [
    "PLS",
    "PLS-Shuffle-NonDominatedUpdate",
    "PLS-Shuffle-DominatingUpdate",
    "SEMO",
]
LABELS = {
    "PLS": r"$s$-PLS",
    "PLS-Shuffle-NonDominatedUpdate": r"$s$-PLS$_{\nprec}$",
    "PLS-Shuffle-DominatingUpdate": r"$s$-PLS$_{\prec}$",
    "SEMO": r"$r$-PLS",
}
MARKERS = {
    "PLS": "o",
    "PLS-Shuffle-NonDominatedUpdate": "D",
    "PLS-Shuffle-DominatingUpdate": "^",
    "SEMO": "s",
}


def record_times(max_eval: int) -> np.ndarray:
    times = []
    for t in range(2, max_eval + 1):
        if (t < 10 or
            (t < 100 and t % 10 == 0) or
            (t < 1_000 and t % 100 == 0) or
            (t < 10_000 and t % 1_000 == 0) or
            (t < 100_000 and t % 10_000 == 0) or
            t % 100_000 == 0):
            times.append(t)
    return np.asarray(times, dtype=int)


def load_hv(root: Path, problem: str, alg: str, runs: int) -> list[np.ndarray]:
    out = []
    for r in range(runs):
        path = root / problem / alg / f"hv-{r}.txt"
        if path.exists():
            y = np.loadtxt(path, ndmin=1)
            out.append(np.maximum(y, 0.0))
    if not out:
        raise FileNotFoundError(f"No HV files for {problem}/{alg}")
    return out


def padded_matrix(runs: list[np.ndarray], length: int) -> np.ndarray:
    rows = []
    for y in runs:
        y = y[:length]
        if len(y) < length:
            y = np.concatenate([y, np.full(length - len(y), y[-1])])
        rows.append(y)
    return np.vstack(rows)


def problem_budget(problem: str) -> int:
    # Matches the published plots: 100D stops at 1e5, larger plots at 1e6.
    size = int(problem.split("-")[1])
    return 100_000 if size == 100 else 1_000_000


def plot_hv(root: Path, out: Path, problem: str, algorithms: list[str], runs: int) -> None:
    times = record_times(problem_budget(problem))
    fig, ax = plt.subplots(figsize=(5, 4), dpi=300)
    for alg in algorithms:
        arr = padded_matrix(load_hv(root, problem, alg, runs), len(times))
        mean, std = arr.mean(axis=0), arr.std(axis=0)
        ax.plot(times, mean, linewidth=2, label=LABELS[alg])
        ax.fill_between(times, mean - std, mean + std, alpha=0.25)
    ax.set_xlabel("Fitness evaluations")
    ax.set_ylabel("Hypervolume")
    ax.ticklabel_format(style="sci", axis="both", scilimits=(0, 0))
    ax.yaxis.set_major_formatter(ticker.ScalarFormatter(useMathText=True))
    ax.legend(frameon=True, fontsize=10)
    fig.tight_layout()
    fig.savefig(out / f"{problem}_hv.png")
    plt.close(fig)


def plot_objective_space(root: Path, out: Path, problem: str, algorithms: list[str], runs: int) -> None:
    fig, ax = plt.subplots(figsize=(5, 4), dpi=300)
    for alg in algorithms:
        hv = load_hv(root, problem, alg, runs)
        finals = np.asarray([x[-1] for x in hv])
        run = int(np.argmin(np.abs(finals - np.median(finals))))
        path = root / problem / alg / f"FUN{run}.csv"
        if not path.exists():
            continue
        pts = pd.read_csv(path, header=None).to_numpy()
        if problem.startswith(("KP", "NK")):
            pts = -pts  # display maximisation problems in natural direction.
        ax.scatter(pts[:, 0], pts[:, 1], facecolors="none", marker=MARKERS[alg], label=LABELS[alg])
    ax.set_xlabel("Objective 1")
    ax.set_ylabel("Objective 2")
    ax.ticklabel_format(style="sci", axis="both", scilimits=(0, 0))
    ax.legend(frameon=True, fontsize=10)
    fig.tight_layout()
    fig.savefig(out / f"{problem}_objective.png")
    plt.close(fig)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--root", required=True, type=Path, help="Directory containing Java output")
    p.add_argument("--out", default=Path("figures"), type=Path)
    p.add_argument("--runs", type=int, default=30)
    p.add_argument("--problems", nargs="*", default=PROBLEMS)
    p.add_argument("--algorithms", nargs="*", default=ALGORITHMS)
    args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for problem in args.problems:
        plot_hv(args.root, args.out, problem, args.algorithms, args.runs)
        plot_objective_space(args.root, args.out, problem, args.algorithms, args.runs)


if __name__ == "__main__":
    main()
