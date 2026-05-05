package core;


import java.util.List;

@SuppressWarnings("serial")
public abstract class AbstractBinaryProblem extends AbstractGenericProblem<BinarySolution>
        implements BinaryProblem {

  public abstract List<Integer> getListOfBitsPerVariable();

  @Override
  public int getBitsFromVariable(int index) {
    return getListOfBitsPerVariable().get(index);
  }

  @Override
  public int getTotalNumberOfBits() {
    int count = 0;
    for (int i = 0; i < this.getNumberOfVariables(); i++) {
      count += this.getListOfBitsPerVariable().get(i);
    }

    return count;
  }

  @Override
  public BinarySolution createSolution() {
    return new DefaultBinarySolution(getListOfBitsPerVariable(), getNumberOfObjectives(), getNumberOfConstraints());
  }
}
