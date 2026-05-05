package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * This class implements an archive containing non-dominated solutions
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 * @author Juan J. Durillo
 * @modified by Zimin Liang, 2025
 */
@SuppressWarnings("serial")
public class NonDominatedSolutionListArchive<S extends Solution<?>> implements Archive<S> {

    /* ==== Generic fields ==== */
    private List<S> solutionList;
    private Comparator<S> dominanceComparator;
    private Comparator<S> equalSolutions = new EqualSolutionsComparator<S>();

    /* ==== Optimisation flags ==== */
    private boolean biObjectiveMode = false;
    private List<S> sortedList; // Used only for 2D case
    private Comparator<S> objective0Comparator;

    private int numObjectives = -1; // Track dimensionality for consistency

    /** Default constructor */
    public NonDominatedSolutionListArchive() {
        this(new DominanceComparator<S>());
    }

    /** Constructor with custom comparator */
    public NonDominatedSolutionListArchive(DominanceComparator<S> comparator) {
        dominanceComparator = comparator;
        solutionList = new ArrayList<>();
    }

    /** Detects and activates bi-objective mode if appropriate */
    private void activateBiObjectiveMode() {
        biObjectiveMode = true;
        sortedList = new ArrayList<>();
        objective0Comparator = Comparator.comparingDouble(s -> s.objectives()[0]);
        // Transfer existing solutions into sorted structure
        for (S s : solutionList) {
            addBiObjective(s);
        }
        solutionList.clear();
    }

    @Override
    public boolean add(S solution) {
        /* --- Sanity check for dimensionality consistency --- */
        int dim = solution.objectives().length;

        if (numObjectives == -1) {
            numObjectives = dim;
        } else if (dim != numObjectives) {
            throw new IllegalStateException(
                String.format("Inconsistent objective dimensions: existing=%d, new=%d",
                              numObjectives, dim)
            );
        }

        /* --- Mode activation logic --- */
        if (dim == 2) {
            if (!biObjectiveMode) {
                activateBiObjectiveMode();
            }
        } else {
            if (biObjectiveMode) {
                throw new IllegalStateException(
                    "Cannot mix bi-objective mode with higher-dimensional solutions"
                );
            }
        }

        /* --- Route to correct insertion method --- */
        if (biObjectiveMode) {
            return addBiObjective(solution);
        } else {
            return addGeneral(solution);
        }
    }

    /** Generic O(n) insertion (legacy mode) */
    private boolean addGeneral(S solution) {
        boolean solutionInserted = false;

        if (solutionList.isEmpty()) {
            solutionList.add(solution);
            return true;
        }

        Iterator<S> iterator = solutionList.iterator();
        boolean isDominated = false;
        boolean isContained = false;

        while ((!isDominated && !isContained) && iterator.hasNext()) {
            S listIndividual = iterator.next();
            int flag = dominanceComparator.compare(solution, listIndividual);

            if (flag == -1) {
                iterator.remove();
            } else if (flag == 1) {
                isDominated = true;
            } else if (flag == 0) {
                int eqFlag = equalSolutions.compare(solution, listIndividual);
                if (eqFlag == 0) isContained = true;
            }
        }

        if (!isDominated && !isContained) {
            solutionList.add(solution);
            solutionInserted = true;
        }

        return solutionInserted;
    }

    /** Optimised O(log n) insertion for bi-objective case */
    private boolean addBiObjective(S solution) {
        double f1 = solution.objectives()[0];
        double f2 = solution.objectives()[1];

        // Binary search by f1
        int index = Collections.binarySearch(sortedList, solution, objective0Comparator);
        if (index < 0) index = -index - 1;

        // Check if dominated by previous
        if (index > 0) {
            S prev = sortedList.get(index - 1);
            if (prev.objectives()[1] <= f2) {
                return false; // dominated
            }
        }

        // Remove all following dominated solutions
        int removeStart = index;
        while (removeStart < sortedList.size() &&
                sortedList.get(removeStart).objectives()[1] >= f2) {
            removeStart++;
        }
        if (removeStart > index)
            sortedList.subList(index, removeStart).clear();

        // Check equality
        if (index < sortedList.size()) {
            S next = sortedList.get(index);
            if (next.objectives()[0] == f1 && next.objectives()[1] == f2)
                return false;
        }

        sortedList.add(index, solution);
        return true;
    }

    /** Join another archive */
    public Archive<S> join(Archive<S> archive) {
        return this.addAll(archive.getSolutionList());
    }

    /** Add all solutions */
    public Archive<S> addAll(List<S> list) {
        for (S s : list) add(s);
        return this;
    }

    /** Retrieve solution list (depends on mode) */
    @Override
    public List<S> getSolutionList() {
        return biObjectiveMode ? sortedList : solutionList;
    }

    @Override
    public int size() {
        return biObjectiveMode ? sortedList.size() : solutionList.size();
    }

    @Override
    public S get(int index) {
        return biObjectiveMode ? sortedList.get(index) : solutionList.get(index);
    }

    public void remove(S candidate) {
        if (biObjectiveMode) sortedList.remove(candidate);
        else solutionList.remove(candidate);
    }
}


/*
 * @SuppressWarnings("serial") public class NonDominatedSolutionListArchive<S
 * extends Solution<?>> implements Archive<S> {
 * 
 * private final List<S> solutionList; private final Comparator<S>
 * dominanceComparator; private final Comparator<S> equalSolutions = new
 * EqualSolutionsComparator<>();
 * 
 * // --- new fields for bi-objective mode --- private boolean biObjectiveMode =
 * false; private TreeMap<Double, S> tree2D = null;
 * 
 * public NonDominatedSolutionListArchive() { this(new DominanceComparator<>());
 * }
 * 
 * public NonDominatedSolutionListArchive(DominanceComparator<S> comparator) {
 * dominanceComparator = comparator; solutionList = new ArrayList<>(); }
 * 
 * public Archive<S> addAll(List<S> list) { for (S solution : list) {
 * this.add(solution); }
 * 
 * return this; }
 * 
 * @Override public boolean add(S solution) { // 1) detect first insertion ->
 * potentially switch into 2-D tree mode if (solutionList.isEmpty()) {
 * solutionList.add(solution); if (solution.objectives().length == 2) {
 * biObjectiveMode = true; tree2D = new TreeMap<>(); double x0 =
 * solution.objectives()[0]; tree2D.put(x0, solution); } else { biObjectiveMode
 * = false; } return true; }
 * 
 * // 2) if in 2-D mode, delegate to log-n tree insertion if (biObjectiveMode) {
 * return addBiObjective(solution); }
 * 
 * // 3) otherwise fall back to original O(n^2) loop return
 * addGeneral(solution); }
 * 
 * private boolean addBiObjective(S sol) { double x = sol.objectives()[0];
 * double y = sol.objectives()[1];
 * 
 * // Duplicate‐check (identical objectives or equalSolutions) for (S existing :
 * tree2D.values()) { double ex = existing.objectives()[0]; double ey =
 * existing.objectives()[1]; if ((ex == x && ey == y) ||
 * equalSolutions.compare(sol, existing) == 0) { return false; } }
 * 
 * // Key‐collision: if there's already a point with the same x if
 * (tree2D.containsKey(x)) { S old = tree2D.get(x); double oldY =
 * old.objectives()[1]; if (oldY <= y) { // old dominates or ties new → reject
 * return false; } else { // new strictly dominates old → remove old and proceed
 * tree2D.remove(x); } }
 * 
 * // Dominated by predecessor? Map.Entry<Double, S> pred =
 * tree2D.floorEntry(x); if (pred != null && pred.getValue().objectives()[1] <=
 * y) { return false; }
 * 
 * // Prune any successors that new dominates NavigableMap<Double, S> tail =
 * tree2D.tailMap(x, false); Iterator<Map.Entry<Double, S>> it =
 * tail.entrySet().iterator(); while (it.hasNext()) { S other =
 * it.next().getValue(); if (other.objectives()[1] >= y) { it.remove(); } else {
 * // since sorting by x, once y_other < y no further removals break; } }
 * 
 * // Insert the new skyline point tree2D.put(x, sol); return true; }
 * 
 * private boolean addGeneral(S solution) { boolean inserted = false;
 * Iterator<S> it = solutionList.iterator(); boolean isDominated = false,
 * isContained = false;
 * 
 * while (!isDominated && !isContained && it.hasNext()) { S curr = it.next();
 * int flag = dominanceComparator.compare(solution, curr); if (flag < 0) {
 * it.remove(); } else if (flag > 0) { isDominated = true; } else { // flag == 0
 * if (equalSolutions.compare(solution, curr) == 0) { isContained = true; } } }
 * if (!isDominated && !isContained) { solutionList.add(solution); inserted =
 * true; } return inserted; }
 * 
 * @Override public List<S> getSolutionList() { if (biObjectiveMode) { // return
 * skyline in ascending x order return new ArrayList<>(tree2D.values()); }
 * return solutionList; }
 * 
 * 
 * public boolean remove(S solution) { if (biObjectiveMode) { // Iterate tree2D
 * entries to find a matching solution Iterator<Map.Entry<Double, S>> it =
 * tree2D.entrySet().iterator(); while (it.hasNext()) { S existing =
 * it.next().getValue(); double ex = existing.objectives()[0]; double ey =
 * existing.objectives()[1]; double sx = solution.objectives()[0]; double sy =
 * solution.objectives()[1];
 * 
 * if ((ex == sx && ey == sy) || equalSolutions.compare(existing, solution) ==
 * 0) { it.remove(); return true; } } return false; } else { // Linear scan over
 * list, using equalSolutions to match Iterator<S> it = solutionList.iterator();
 * while (it.hasNext()) { S existing = it.next(); if
 * (equalSolutions.compare(existing, solution) == 0) { it.remove(); return true;
 * } } return false; } }
 * 
 * @Override public int size() { return biObjectiveMode ? tree2D.size() :
 * solutionList.size(); }
 * 
 * @Override public S get(int idx) { return getSolutionList().get(idx); }
 * 
 * public void sort(Comparator<? super S> comparator) {
 * solutionList.sort(comparator); }
 * 
 * }
 */