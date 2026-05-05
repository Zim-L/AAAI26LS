package core;

/**
 * Interface representing binary (bitset) solutions
 *
 * @author Antonio J. Nebro <antonio@lcc.uma.es>
 */
public interface BinarySolution extends Solution<BinarySet> {
  int getNumberOfBits(int index) ;
  int getTotalNumberOfBits() ;
}
