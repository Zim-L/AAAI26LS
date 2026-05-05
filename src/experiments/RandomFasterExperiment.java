package experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import algorithms.PLS;
import algorithms.PLSShuffleDominating;
import algorithms.PLSShuffleNonDominated;
import algorithms.RandomSearch;
import algorithms.SEMO;
import algorithms.ZAlgorithm;
import core.BinarySet;
import core.BinarySolution;
import core.DefaultFileOutputContext;
import core.DominanceComparator;
import core.PermutationSolution;
import core.Problem;
import core.Solution;
import core.SolutionListOutput;
import problems.MOKP;
import problems.MONKLand;
import problems.MOQAP;
import problems.MOTSP;

/**
 * @author Zimin Liang
 * Experiments for the paper
 * "Random is Faster than Systematic in Multi-Objective Local Search".
 */
public class RandomFasterExperiment {

    private static final DominanceComparator DOM = new DominanceComparator();

    private enum Mode { MAIN, NEIGHBOURS, RANDOM }
    private enum Variant { PLS, PLS_NONDOMINATED, PLS_DOMINATING, SEMO, RANDOM }

    private static class Config {
        Mode mode = Mode.MAIN;
        String dataDir = "D:/SEMOPLS/Data";
        String outDir = "D:/experiments-LSanytime/clean";
        int runs = 30;
        int startRun = 0;
        int threads = 16;
        int budget = 1_000_000;
        boolean allSizes = true;

        static Config fromArgs(String[] args) {
            Config c = new Config();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--mode": c.mode = Mode.valueOf(args[++i].toUpperCase()); break;
                    case "--data": c.dataDir = args[++i]; break;
                    case "--out": c.outDir = args[++i]; break;
                    case "--runs": c.runs = Integer.parseInt(args[++i]); break;
                    case "--start": c.startRun = Integer.parseInt(args[++i]); break;
                    case "--threads": c.threads = Integer.parseInt(args[++i]); break;
                    case "--budget": c.budget = Integer.parseInt(args[++i]); break;
                    case "--only100": c.allSizes = false; break;
                    default: throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            return c;
        }
    }

    public static void main(String[] args) {
        Config cfg = Config.fromArgs(args);
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(cfg.threads));

        List<Problem> problems = loadProblems(cfg.dataDir, cfg.allSizes || cfg.mode == Mode.MAIN);
        List<Integer> runs = IntStream.range(cfg.startRun, cfg.startRun + cfg.runs)
                .boxed().collect(Collectors.toList());

        runs.parallelStream().forEach(run -> {
            for (Problem problem : problems) {
                switch (cfg.mode) {
                    case MAIN: runMainComparison(problem, run, cfg); break;
                    case NEIGHBOURS: runNeighbourCounting(problem, run, cfg); break;
                    case RANDOM: runRandomSampling(problem, run, cfg); break;
                }
            }
        });
    }

    /** Main performance experiment: HV trajectories and final archive files. */
    private static void runMainComparison(Problem problem, int run, Config cfg) {
        Solution init = feasibleInitialSolution(problem);
        Map<Variant, List<Double>> hv = new HashMap<>();
        Map<String, double[]> refs = referencePoints();
        int initialBudget = cfg.budget;

        int tPLS = runAndSave(Variant.PLS, problem, init, run, initialBudget, cfg,
                hvMonitor(problem, hv, Variant.PLS, refs));
        int tND = runAndSave(Variant.PLS_NONDOMINATED, problem, init, run, initialBudget, cfg,
                hvMonitor(problem, hv, Variant.PLS_NONDOMINATED, refs));
        int tDom = runAndSave(Variant.PLS_DOMINATING, problem, init, run, initialBudget, cfg,
                hvMonitor(problem, hv, Variant.PLS_DOMINATING, refs));

        // r-PLS is stopped at the largest number of evaluations used by the s-PLS variants.
        int semoBudget = Collections.max(List.of(tPLS, tND, tDom));
        runAndSave(Variant.SEMO, problem, init, run, semoBudget, cfg,
                hvMonitor(problem, hv, Variant.SEMO, refs));

        for (Map.Entry<Variant, List<Double>> e : hv.entrySet()) {
            writeList(e.getValue(), algDir(cfg, problem, e.getKey()) + "/hv-" + run + ".txt");
        }
    }

    /** Distribution experiment: for each sampled archive state, count each solution's good neighbours. */
    private static void runNeighbourCounting(Problem problem, int run, Config cfg) {
        Solution init = feasibleInitialSolution(problem);
        List<List<Integer>> idx = neighbourIndices(problem);
        BiFunction<Solution, List<Integer>, Solution> neighbour = neighbourOperator(problem);

        Map<Variant, List<List<Integer>>> counts = new HashMap<>();
        Consumer<ZAlgorithm> monitor = alg -> {
            if ((alg.getT() - 1) % idx.size() != 0) return;
            Variant v = alg.getName().equals("SEMO") ? Variant.SEMO : Variant.PLS;
            counts.computeIfAbsent(v, k -> new ArrayList<>())
                    .add(countGoodNeighbours(problem, alg.getArchive(), idx, neighbour));
        };

        int tPLS = runAndSave(Variant.PLS, problem, init, run, cfg.budget, cfg, monitor);
        runAndSave(Variant.SEMO, problem, init, run, (int)(1.2 * tPLS), cfg, monitor);

        for (Map.Entry<Variant, List<List<Integer>>> e : counts.entrySet()) {
            writeMatrix(e.getValue(), algDir(cfg, problem, e.getKey()) + "/neighboursInfo-" + run + ".txt");
        }
    }

    /** Random sampling used for estimating reference points: save nondominated random solutions. */
    private static void runRandomSampling(Problem problem, int run, Config cfg) {
        runAndSave(Variant.RANDOM, problem, null, run, cfg.budget, cfg, alg -> {});
    }

    private static Consumer<ZAlgorithm> hvMonitor(Problem problem, Map<Variant, List<Double>> hv,
                                                   Variant variant, Map<String, double[]> refs) {
        return alg -> {
            if (!isRecordTime(alg.getT())) return;
            double[] ref = refs.get(problem.getName());
            if (ref == null) throw new IllegalArgumentException("Missing reference point for " + problem.getName());
            hv.computeIfAbsent(variant, k -> new ArrayList<>())
                    .add(hypervolume2DMin(alg.getArchive(), ref));
        };
    }

    private static boolean isRecordTime(int t) {
        return t < 10
                || (t < 100 && t % 10 == 0)
                || (t < 1_000 && t % 100 == 0)
                || (t < 10_000 && t % 1_000 == 0)
                || (t < 100_000 && t % 10_000 == 0)
                || (t % 100_000 == 0);
    }

    private static int runAndSave(Variant variant, Problem problem, Solution init, int run,
                                  int budget, Config cfg, Consumer<ZAlgorithm> monitor) {
        ZAlgorithm alg = makeAlgorithm(variant, problem, budget);
        alg.setMonitor(monitor);

        long t0 = System.currentTimeMillis();
        alg.run();
        long ms = System.currentTimeMillis() - t0;
        saveFinalArchive(alg, algDir(cfg, problem, variant), run, ms);
        return alg.getT();
    }

    private static ZAlgorithm makeAlgorithm(Variant variant, Problem problem, int budget) {
        Supplier<List<List<Integer>>> idx = () -> neighbourIndices(problem);
        BiFunction<Solution, List<Integer>, Solution> op = neighbourOperator(problem);

        switch (variant) {
            case PLS: {
                PLS a = new PLS(problem, idx, op, DOM);
                a.setPrompt(false); a.setMaxEvaluations(budget); return a;
            }
            case PLS_NONDOMINATED: {
                PLSShuffleNonDominated a = new PLSShuffleNonDominated(problem, idx, op, DOM);
                a.setPrompt(false); a.setMaxEvaluations(budget); a.shuffling = false; return a;
            }
            case PLS_DOMINATING: {
                PLSShuffleDominating a = new PLSShuffleDominating(problem, idx, op, DOM);
                a.setPrompt(false); a.setMaxEvaluations(budget); a.shuffling = false; return a;
            }
            case SEMO: {
                SEMO a = new SEMO(problem, idx, op, DOM);
                a.setPrompt(false); a.setMaxEvaluations(budget); return a;
            }
            case RANDOM:
                return new RandomSearch(problem, budget);
            default:
                throw new IllegalArgumentException("Unsupported algorithm variant: " + variant);
        }
    }

    private static List<Integer> countGoodNeighbours(Problem problem, List<Solution> archive,
                                                     List<List<Integer>> indices,
                                                     BiFunction<Solution, List<Integer>, Solution> op) {
        List<Integer> row = new ArrayList<>();
        for (Solution sol : archive) {
            int count = 0;
            for (List<Integer> index : indices) {
                Solution n = op.apply(sol, index);
                problem.evaluate(n);
                if (notDominatedByArchive(n, archive)) count++;
            }
            row.add(count);
        }
        return row;
    }

    private static boolean notDominatedByArchive(Solution candidate, List<Solution> archive) {
        for (Solution s : archive) {
            if (DOM.compare(s, candidate) == -1) return false;
        }
        return true;
    }

    /** 2D hypervolume for minimisation. Objective values outside the reference box are ignored. */
    private static double hypervolume2DMin(List<Solution> archive, double[] ref) {
        List<double[]> pts = archive.stream()
                .map(s -> new double[]{s.objectives()[0], s.objectives()[1]})
                .filter(p -> p[0] <= ref[0] && p[1] <= ref[1])
                .sorted(Comparator.comparingDouble(p -> p[0]))
                .collect(Collectors.toList());
        if (pts.isEmpty()) return 0.0;

        double hv = 0.0;
        double bestY = ref[1];
        double lastX = ref[0];
        for (int i = pts.size() - 1; i >= 0; i--) {
            double x = pts.get(i)[0], y = pts.get(i)[1];
            if (y < bestY) {
                hv += (lastX - x) * (ref[1] - y);
                lastX = x;
                bestY = y;
            }
        }
        return Math.max(0.0, hv);
    }

    private static List<List<Integer>> neighbourIndices(Problem problem) {
        int n = problem.getNumberOfVariables();
        if (problem.getName().contains("NK")) {
            return IntStream.range(0, n).mapToObj(i -> List.of(i)).collect(Collectors.toList());
        }
        return IntStream.range(0, n).boxed()
                .flatMap(i -> IntStream.range(i + 1, n).mapToObj(j -> List.of(i, j)))
                .collect(Collectors.toList());
    }

    private static BiFunction<Solution, List<Integer>, Solution> neighbourOperator(Problem problem) {
        if (problem.createSolution() instanceof BinarySolution) {
            if (problem.getName().contains("NK")) {
                return (x, idx) -> {
                    BinarySolution s = (BinarySolution) x.copy();
                    s.attributes().clear();
                    s.variables().get(idx.get(0)).flip(0);
                    return s;
                };
            }
            return (x, idx) -> {
                BinarySolution s = (BinarySolution) x.copy();
                s.attributes().clear();
                s.variables().get(idx.get(0)).flip(0);
                s.variables().get(idx.get(1)).flip(0);
                return s;
            };
        }
        if (problem.getName().contains("TSP")) {
            return (x, idx) -> {
                PermutationSolution s = (PermutationSolution) x.copy();
                s.attributes().clear();
                Collections.reverse(s.variables().subList(idx.get(0), idx.get(1) + 1));
                return s;
            };
        }
        if (problem.getName().contains("QAP")) {
            return (x, idx) -> {
                PermutationSolution s = (PermutationSolution) x.copy();
                s.attributes().clear();
                Collections.swap(s.variables(), idx.get(0), idx.get(1));
                return s;
            };
        }
        throw new IllegalArgumentException("No neighbourhood operator for " + problem.getName());
    }

    private static Solution feasibleInitialSolution(Problem problem) {
        Solution s;
        do {
            s = (Solution) problem.createSolution();
            problem.evaluate(s);
        } while (violatesConstraints(s));
        return s;
    }

    private static boolean violatesConstraints(Solution s) {
        if (!(s instanceof BinarySolution)) return false;
        for (double v : ((BinarySolution) s).constraints()) {
            if (v != 0.0) return true;
        }
        return false;
    }

    private static List<Problem> loadProblems(String dataDir, boolean allSizes) {
        if (!dataDir.endsWith("/") && !dataDir.endsWith("\\")) dataDir += File.separator;
        int[] sizes = allSizes ? new int[]{100, 200, 500} : new int[]{100};
        List<Problem> problems = new ArrayList<>();
        for (int n : sizes) problems.add(new MOKP(n).load(dataDir + "KP-" + n + ".txt"));
        for (int n : sizes) problems.add(new MONKLand(n, 10).load(dataDir + "NK-" + n + "-10.txt"));
        for (int n : sizes) problems.add(new MOTSP(n).load(dataDir + "TSP-" + n + ".txt"));
        for (int n : sizes) problems.add(new MOQAP(n).load(dataDir + "QAP-" + n + ".txt"));
        return problems;
    }

    /*
     * Following reference point are computed through Random Sampling, see the paper
     * Empirical Comparison between MOEAs and Local Search on Multi-Objective Combinatorial Optimisation Problems
     * GECCO 2024  https://doi.org/10.1145/3638529.3654077
     */
    private static Map<String, double[]> referencePoints() {
        Map<String, double[]> r = new HashMap<>();
        r.put("KP-100", new double[]{-2515.0000000000000000, -2716.0000000000000000});
        r.put("KP-200", new double[]{-5520.0000000000000000, -5320.0000000000000000});
        r.put("KP-500", new double[]{-13840.0000000000000000, -13696.0000000000000000});
        r.put("NK-100-10", new double[]{-0.4557920055974312, -0.4594281083134446});
        r.put("NK-200-10", new double[]{-0.4453927705633157, -0.4558792735132561});
        r.put("NK-500-10", new double[]{-0.4653126621319031, -0.4775838050817161});
        r.put("TSP-100", new double[]{56.6190727660660968, 60.0963967590472024});
        r.put("TSP-200", new double[]{107.8075785448631194, 110.4330006768590238});
        r.put("TSP-500", new double[]{261.3677799776644974, 264.5092881122234303});
        r.put("QAP-100", new double[]{1290375369.1054358482360840, 1293185711.3079488277435303});
        r.put("QAP-200", new double[]{5370058024.8078613281250000, 5387024686.8716564178466797});
        r.put("QAP-500", new double[]{31692372895.8438377380371094, 31647745621.1098556518554688});
        return r;
    }

    private static String algDir(Config cfg, Problem p, Variant v) {
        return cfg.outDir + File.separator + p.getName() + File.separator + algName(v);
    }

    private static String algName(Variant v) {
        switch (v) {
            case PLS: return "PLS";
            case PLS_NONDOMINATED: return "PLS-Shuffle-NonDominatedUpdate";
            case PLS_DOMINATING: return "PLS-Shuffle-DominatingUpdate";
            case SEMO: return "SEMO";
            case RANDOM: return "RandomSearch";
            default: throw new IllegalArgumentException();
        }
    }

    private static void saveFinalArchive(ZAlgorithm alg, String dir, int run, long durationMs) {
        new File(dir).mkdirs();
        try (PrintWriter w = new PrintWriter(new File(dir, "INFO" + run + ".txt"))) {
            w.println(alg.getProblem().getName());
            w.println(alg.getName());
            w.println("Duration(ms):" + durationMs);
            w.println("Evaluations:" + alg.getT());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<? extends Solution<?>> result = copyResult(alg);
        new SolutionListOutput(result)
                .setVarFileOutputContext(new DefaultFileOutputContext(dir + File.separator + "VAR" + run + ".csv", ","))
                .setFunFileOutputContext(new DefaultFileOutputContext(dir + File.separator + "FUN" + run + ".csv", ","))
                .print();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<? extends Solution<?>> copyResult(ZAlgorithm alg) {
        List<Solution<?>> out = new ArrayList<>();
        for (Object raw : alg.getResult()) {
            Solution s = (Solution) raw;
            out.add((Solution<?>) s.copy());
        }
        return out;
    }

    private static void writeList(List<Double> data, String path) {
        File f = new File(path); f.getParentFile().mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (double x : data) {
                w.write(String.format(java.util.Locale.US, "%.16f", x));
                w.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeMatrix(List<List<Integer>> rows, String path) {
        File f = new File(path); f.getParentFile().mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (List<Integer> row : rows) {
                w.write(row.stream().map(String::valueOf).collect(Collectors.joining(" ")));
                w.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
