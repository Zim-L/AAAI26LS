package algorithms;

import java.util.List;
import java.util.function.Consumer;

import core.Problem;

public interface ZAlgorithm<S> extends Runnable {
	public List<S> getPopulation();
	public List<S> getArchive();
	public int getEvaluations();
	public int getT();
	public String getName();
	public Problem getProblem();
	public List<S> getResult();
	public void setMonitor(Consumer<ZAlgorithm> monitor);
}
