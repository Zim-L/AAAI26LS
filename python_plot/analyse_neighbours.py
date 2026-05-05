#!/usr/bin/env python3
"""Analyse the distribution of good-neighbour counts.

The Java runner writes one line per sampled archive state. Each line contains,
for all archive solutions at that state, the number of neighbours not dominated
by the current archive. This script fits common discrete distributions and plots
whether the chi-square test rejects each model.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path
import numpy as np
import matplotlib.pyplot as plt
from scipy.optimize import minimize_scalar
from scipy.stats import poisson, geom, binom, chisquare

PROBLEMS = {"KP-100": 4950, "NK-100-10": 100, "TSP-100": 4950, "QAP-100": 4950}
ALGORITHMS = ["PLS", "SEMO"]
DISTS = ["Uniform", "Poisson", "Binomial", "Zipf", "Geometric"]


def load_counts(root: Path, problem: str, alg: str, runs: int) -> list[list[np.ndarray]]:
    out = []
    for r in range(runs):
        path = root / problem / alg / f"neighboursInfo-{r}.txt"
        if not path.exists():
            continue
        states = []
        for line in path.read_text().splitlines():
            if line.strip():
                states.append(np.fromiter(map(int, line.split()), dtype=int))
        out.append(states)
    if not out:
        raise FileNotFoundError(f"No neighbour-count files for {problem}/{alg}")
    return out


def fit(dist: str, x: np.ndarray, support: int) -> np.ndarray:
    k = np.arange(support)
    if dist == "Uniform":
        p = np.ones(support) / support
    elif dist == "Poisson":
        p = poisson.pmf(k, max(float(x.mean()), 1e-12))
    elif dist == "Geometric":
        q = 1.0 / (float(x.mean()) + 1.0)
        p = (1 - q) ** k * q
    elif dist == "Binomial":
        q = min(max(float(x.mean()) / support, 1e-12), 1 - 1e-12)
        p = binom.pmf(k, support, q)
    elif dist == "Zipf":
        vals = x.astype(float) + 1.0
        ranks = np.arange(1, support + 1, dtype=float)
        def nll(s: float) -> float:
            z = np.sum(ranks ** (-s))
            return -(-s * np.sum(np.log(vals)) - len(vals) * math.log(z))
        s = minimize_scalar(nll, bounds=(1.01, 10.0), method="bounded").x
        p = ranks ** (-s)
    else:
        raise ValueError(dist)
    p = np.asarray(p, dtype=float)
    p /= p.sum()
    return p


def accepted(dist: str, x: np.ndarray, support: int, alpha: float) -> bool:
    obs = np.bincount(x, minlength=support)
    exp = obs.sum() * fit(dist, x, support)
    mask = (exp > 1e-12) | (obs > 0)
    if mask.sum() < 2:
        return False
    _, p_value = chisquare(obs[mask], f_exp=exp[mask])
    return bool(p_value >= alpha)


def majority_acceptance(runs: list[list[np.ndarray]], support: int, alpha: float) -> dict[str, np.ndarray]:
    horizon = min(len(r) for r in runs)
    ans = {}
    for dist in DISTS:
        mat = np.zeros((len(runs), horizon), dtype=bool)
        for i, states in enumerate(runs):
            for t in range(horizon):
                mat[i, t] = accepted(dist, states[t], support, alpha)
        ans[dist] = mat.mean(axis=0) > 0.5
    return ans


def plot_gof(root: Path, out: Path, problem: str, runs: int, alpha: float) -> None:
    fig, ax = plt.subplots(figsize=(7, 2.7), dpi=300)
    yticks, ylabels = [], []
    row = 0
    for dist in DISTS:
        for alg, marker in [("PLS", "|"), ("SEMO", "|")]:
            data = load_counts(root, problem, alg, runs)
            ok = majority_acceptance(data, PROBLEMS[problem], alpha)[dist]
            xs = np.where(ok)[0]
            ax.scatter(xs, np.full_like(xs, row), marker=marker, s=120, label=alg if row < 2 else None)
            yticks.append(row)
            ylabels.append(f"{dist} / {alg}")
            row += 1
    ax.set_yticks(yticks)
    ax.set_yticklabels(ylabels)
    ax.set_xlabel("Sampled archive state")
    ax.legend(frameon=True, ncol=2, loc="upper right")
    fig.tight_layout()
    fig.savefig(out / f"{problem}_goodness_of_fit.png")
    plt.close(fig)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--root", required=True, type=Path)
    p.add_argument("--out", default=Path("figures"), type=Path)
    p.add_argument("--runs", type=int, default=30)
    p.add_argument("--alpha", type=float, default=0.05)
    p.add_argument("--problems", nargs="*", default=list(PROBLEMS))
    args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for problem in args.problems:
        plot_gof(args.root, args.out, problem, args.runs, args.alpha)


if __name__ == "__main__":
    main()
