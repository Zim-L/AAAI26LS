package core;


@SuppressWarnings("serial")
public abstract class AbstractIntegerPermutationProblem
    extends AbstractGenericProblem<PermutationSolution<Integer>> implements
    PermutationProblem<PermutationSolution<Integer>> {

  @Override
  public PermutationSolution<Integer> createSolution() {
    return new IntegerPermutationSolution(getLength(), getNumberOfObjectives()) ;
  }
}
