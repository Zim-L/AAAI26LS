package core;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * This class implements an archive containing non-duplicated solutions
 *
 * @author Zimin Liang <zimin.liang@outlook.com>
 * This is changed from the jMetal NonDominatedSolutionListArchive jMetal
 * 	 @author Antonio J. Nebro <antonio@lcc.uma.es>
 * 	 @author Juan J. Durillo
 */
@SuppressWarnings("serial")
public class UnboundedListArchive<S extends Solution<?>> implements Archive<S> {
	private List<S> solutionList;
	private Comparator<S> equalSolutions = new EqualSolutionsComparator<S>();

	/**
	 * Constructor
	 */
	public UnboundedListArchive() {
		solutionList = new ArrayList<>();
	}

	/**
	 * Inserts a solution in the list
	 *
	 * @param solution The solution to be inserted.
	 * @return true if the operation success, and false if the solution is dominated
	 *         or if an identical individual exists. The decision variables can be
	 *         null if the solution is read from a file; in that case, the
	 *         domination tests are omitted
	 */
	@Override
	public boolean add(S solution) {
		boolean solutionInserted = false;
		if (solutionList.size() == 0) {
			solutionList.add(solution);
			solutionInserted = true;
		} else {
			Iterator<S> iterator = solutionList.iterator();

			boolean isContained = false;
			while (((!isContained)) && (iterator.hasNext())) {
				S listIndividual = iterator.next();
				int equalflag = equalSolutions.compare(solution, listIndividual);
				if (equalflag == 0) // solutions are equals
					isContained = true;
			}

			if (!isContained) {
				solutionList.add(solution);
				solutionInserted = true;
			}

			return solutionInserted;
		}

		return solutionInserted;
	}

	public Archive<S> join(Archive<S> archive) {
		return this.addAll(archive.getSolutionList());
	}

	public Archive<S> addAll(List<S> list) {
		for (S solution : list) {
			this.add(solution);
		}

		return this;
	}

	@Override
	public List<S> getSolutionList() {
		return solutionList;
	}

	@Override
	public int size() {
		return solutionList.size();
	}

	@Override
	public S get(int index) {
		return solutionList.get(index);
	}
}
