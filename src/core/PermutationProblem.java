package core;

/**
 * Interface representing permutation problems
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 */
public interface PermutationProblem<S extends PermutationSolution<?>> extends Problem<S> {
  int getLength() ;
}
