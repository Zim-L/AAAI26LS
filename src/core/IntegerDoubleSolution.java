package core;


/**
 * Interface representing solutions containing an integer solution and a double solution
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 */
@Deprecated
public interface IntegerDoubleSolution extends Solution<Solution<?>> {
  IntegerSolution getIntegerSolution() ;
  DoubleSolution getDoubleSolution() ;
}
