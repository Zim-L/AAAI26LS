package algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import core.Algorithm;
import core.DominanceComparator;
import core.JMetalRandom;
import core.NonDominatedSolutionListArchive;
import core.Problem;
import core.Solution;



public class PLS<S extends Solution> implements Algorithm<List<S>>, ZAlgorithm<S>, LocalSearchTester<S> {

	
	private static final long serialVersionUID = -4985021281052183946L;
	private int evaluations = 0;
	private Problem<S> problem;
	private Supplier<List<List<Integer>>> indexGenerator;
	private BiFunction<S, List<Integer>, S> neighbourGenerator;
	private DominanceComparator<S> dominance;
	private NonDominatedSolutionListArchive archive;
	private List<S> initSolutions = null;
	private S candidate;
	private Consumer<ZAlgorithm> monitor = null;
	public Consumer<ZAlgorithm> getMonitor() {
		return monitor;
	}
	public void setMonitor(Consumer<ZAlgorithm> monitor) {
		this.monitor = monitor;
	}
	
	private List<List<Integer>> neighbourIndices;

	private int maxEvaluations = Integer.MAX_VALUE;
	private boolean prompt = true;
	private NonDominatedSolutionListArchive<S> explore;

	public PLS(Problem<S> problem, Supplier<List<List<Integer>>> indexGenerator, 
			BiFunction<S, List<Integer>, S> neighborGenerator, DominanceComparator<S> dominance) {
		this.problem = problem;
		this.indexGenerator = indexGenerator;
		this.neighbourGenerator = neighborGenerator;
		this.dominance = dominance;
		this.archive = new NonDominatedSolutionListArchive<S>();
		
		neighbourIndices = new ArrayList<List<Integer>>();
		indexGenerator.get().forEach(neighbourIndices::add);
	}

	@Override
	public String getName() {
		return "PLS";
	}

	@Override
	public String getDescription() {
		return "Pareto Local Search";
	}

	@Override
	public void run() {
		if (initSolutions == null) {
			initSolutions = new ArrayList<S>();
			evaluations = 0;
			while (initSolutions.size()==0) {
				S initSol = problem.createSolution();
				problem.evaluate(initSol);
				evaluations++;
				if (Arrays.stream(initSol.constraints()).allMatch(c -> c == 0)) initSolutions.add(initSol);
			}
		} else {
			initSolutions.forEach(i -> problem.evaluate(i));
			evaluations = initSolutions.size();
		}
		archive.addAll(initSolutions);
		if (monitor!=null) monitor.accept(this);

		explore = new NonDominatedSolutionListArchive<S>();
		explore.addAll(initSolutions);
		
		while (explore.size() > 0) {
			if (prompt)
				System.out.println("Explore list size: " + explore.size() + ";  \tevaluations: " + evaluations
						+ ";  \tarchive size: " + archive.size());

			// random selection
			candidate = (S) sample(explore.getSolutionList());

			// neighbourhood exploration
			for (List<Integer> index : neighbourIndices) {
				S nb = neighbourGenerator.apply(candidate, index);
				if (evaluations < maxEvaluations) {
					problem.evaluate(nb);
					evaluations++;
					boolean added = archive.add(nb);
					if (added) explore.add(nb);
					if (monitor!=null) monitor.accept(this);
				} else
					break;
			}

			explore.remove(candidate);

			if (evaluations >= maxEvaluations)
				break;
		}
	}

	private S sample(List<S> l) {
		return l.get(JMetalRandom.getInstance().nextInt(0, l.size() - 1));
	}

	public List<S> getPhase1solutions() {
		return initSolutions;
	}

	@Override
	public List<S> getResult() {
		return archive.getSolutionList();
	}

	public List<S> getInitSolutions() {
		return initSolutions;
	}

	public void setInitSolutions(List<S> initSolutions) {
		this.initSolutions = initSolutions;
	}

	public int getMaxEvaluations() {
		return maxEvaluations;
	}

	public void setMaxEvaluations(int maxEvaluations) {
		this.maxEvaluations = maxEvaluations;
	}

	public boolean isPrompt() {
		return prompt;
	}

	public void setPrompt(boolean prompt) {
		this.prompt = prompt;
	}

	public int getEvaluations() {
		return evaluations;
	}

	@Override
	public List<S> getPopulation() {
		return archive.getSolutionList();
	}

	@Override
	public List<S> getArchive() {
		return archive.getSolutionList();
	}

	@Override
	public int getT() {
		return evaluations;
	}

	@Override
	public Problem getProblem() {
		return problem;
	}
	
	@Override
	public S getCandidate() {
		return candidate;
	}
	@Override
	public List<S> getExplore() {
		return explore.getSolutionList();
	}

}
