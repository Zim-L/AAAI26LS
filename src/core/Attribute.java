package core;

import java.util.Comparator;

public interface Attribute<S> {
  String getAttributeId() ;
  Comparator<S> getSolutionComparator() ;
}
