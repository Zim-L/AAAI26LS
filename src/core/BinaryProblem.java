package core;

import java.util.List;

/**
 * Interface representing binary problems
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 */
public interface BinaryProblem extends Problem<BinarySolution> {
  List<Integer> getListOfBitsPerVariable() ;
  int getBitsFromVariable(int index) ;
  int getTotalNumberOfBits() ;
}
