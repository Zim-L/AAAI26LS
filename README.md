
# Random is Faster than Systematic in Multi-Objective Local Search

This repository contains the source code for the AAAI 2026 paper:

> Zimin Liang and Miqing Li.  
> **Random is Faster than Systematic in Multi-Objective Local Search.**  
> Proceedings of the AAAI Conference on Artificial Intelligence, 2026.  
> DOI: https://doi.org/10.1609/aaai.v40i43.41036

The paper studies a simple but important question in Pareto-based multi-objective local search:

> When a solution is selected from the current non-dominated archive, should we explore its neighbourhood systematically, or should we sample only one random neighbour?

Although systematic exploration avoids rechecking the same neighbours, the experiments show that random sampling is often faster because many selected archive solutions have few useful neighbours. The code in this repository reproduces the main empirical comparison and the analysis of the distribution of useful neighbours.

## Implementation basis

The experimental code is implemented in Java and is based on the [jMetal](https://github.com/jMetal/jMetal) platform for metaheuristic optimisation. In particular, the code follows the common jMetal-style abstractions for problems, solutions, algorithms, dominance comparison, and solution-list output. The jMetal components are mainly stored in the core folder in this repo.

Some classes have been adapted or simplified for the specific experiments in this paper, so the repository should be read as a self-contained experimental implementation built on jMetal-style infrastructure, rather than as a direct plug-in module for an unmodified jMetal release.

## Recommended entry point

If you are reading the code for the first time, start from:

```text
src/experiments/RandomFasterExperiment.java
````

This class is intended to be the clean entry point for the experiments. It replaces several older experimental drivers and groups the main experimental modes in one place.

The main modes are:

```text
main        Run the main comparison between systematic and randomised PLS variants.
neighbours  Record the number of useful neighbours of archive solutions.
random      Generate random-sampling results, mainly for reference-point checking.
```

## Algorithms compared

The main comparison includes four local-search variants:

| Code name                        |      Paper notation | Description                                                                                          |
| -------------------------------- | ------------------: | ---------------------------------------------------------------------------------------------------- |
| `PLS`                            |             `s`-PLS | Systematic PLS using exhaustive neighbourhood exploration.                                           |
| `PLS-Shuffle-NonDominatedUpdate` | `s`-PLS<sub>⊁</sub> | First-improvement style variant that stops once a neighbour is not dominated by the archive.         |
| `PLS-Shuffle-DominatingUpdate`   | `s`-PLS<sub>≺</sub> | First-improvement style variant that stops once a neighbour dominates at least one archive solution. |
| `SEMO`                           |             `r`-PLS | Randomised PLS that samples one random neighbour at a time.                                          |

In the paper, `SEMO` is used as the implementation of the randomised Pareto local search mechanism.

## Problems and neighbourhoods

The experiments use four bi-objective combinatorial optimisation problems:

| Problem                                                | Encoding      | Neighbourhood |
| ------------------------------------------------------ | ------------- | ------------- |
| Multi-objective knapsack problem (`KP`)                | Binary string | 2-bit flip    |
| Multi-objective NK-landscape (`NK`)                    | Binary string | 1-bit flip    |
| Multi-objective travelling salesperson problem (`TSP`) | Permutation   | 2-opt         |
| Multi-objective quadratic assignment problem (`QAP`)   | Permutation   | 2-swap        |

The tested sizes are 100, 200, and 500 variables/items/cities/facilities, depending on the problem.

## Repository structure

```text
.
├── README.md
├── LICENSE
├── src/
│   ├── algorithms/          # Implementations of PLS, SEMO, and variants
│   ├── core/                # Solution, problem, dominance, and output utilities from the jMetal platform
│   ├── problems/            # KP, NK, TSP, and QAP problem definitions
│   └── experiments/
│       └── RandomFasterExperiment.java
└── python_plot/
    ├── plot_results.py          # Hypervolume and objective-space plots
    └── analyse_neighbours.py    # Distribution analysis of useful-neighbour counts
```

## Running the experiments

Example command for the main comparison:

```bash
java experiments.RandomFasterExperiment \
  --mode main \
  --data ./Data \
  --out ./results/main \
  --runs 30 \
  --threads 16 \
  --budget 1000000
```

Example command for collecting useful-neighbour counts:

```bash
java experiments.RandomFasterExperiment \
  --mode neighbours \
  --data ./Data \
  --out ./results/neighbours \
  --runs 30 \
  --threads 16 \
  --budget 1000000
```

Example command for random sampling:

```bash
java experiments.RandomFasterExperiment \
  --mode random \
  --data ./Data \
  --out ./results/random \
  --runs 30 \
  --threads 16 \
  --budget 100000
```

The `--data` argument should point to the folder containing the problem instance files, such as:

```text
KP-100.txt
KP-200.txt
KP-500.txt
NK-100-10.txt
NK-200-10.txt
NK-500-10.txt   // This file exceeds the upload limit 25MB. You may generate your own with the MONKLand.java 
TSP-100.txt
TSP-200.txt
TSP-500.txt
QAP-100.txt
QAP-200.txt
QAP-500.txt
```

## Output files

For each problem, algorithm, and run, the Java code writes files such as:

```text
FUN<run>.csv
VAR<run>.csv
INFO<run>.csv
hv-<run>.txt
neighboursInfo-<run>.txt
```

Their meanings are:

| File                       | Meaning                                                              |
| -------------------------- | -------------------------------------------------------------------- |
| `FUN<run>.csv`             | Objective vectors of the final archive.                              |
| `VAR<run>.csv`             | Decision variables of the final archive.                             |
| `INFO<run>.csv`            | Runtime and evaluation-count information.                            |
| `hv-<run>.txt`             | Hypervolume trajectory recorded during the run.                      |
| `neighboursInfo-<run>.txt` | Counts of useful neighbours for archive solutions during the search. |

The `neighboursInfo` files are used to analyse the distribution of useful neighbours. In the paper, this distribution is used to explain why random sampling can be faster than systematic exploration.

## Plotting and analysis

After running the experiments, use the Python scripts in `python_plot/`.

For hypervolume and objective-space plots:

```bash
python python_plot/plot_results.py --result-dir ./results/main --out-dir ./figures
```

For analysing useful-neighbour distributions:

```bash
python python_plot/analyse_neighbours.py --result-dir ./results/neighbours --out-dir ./figures/neighbour_analysis
```

The Python scripts assume the result directory follows the layout generated by `RandomFasterExperiment.java`.

## Citation

If you use this code, please cite:

```bibtex
@inproceedings{liang2026random,
  title     = {Random is Faster than Systematic in Multi-Objective Local Search},
  author    = {Liang, Zimin and Li, Miqing},
  booktitle = {Proceedings of the AAAI Conference on Artificial Intelligence},
  year      = {2026},
  doi       = {10.1609/aaai.v40i43.41036}
}
```

## Licence
This repository is released under the MIT Licence.
