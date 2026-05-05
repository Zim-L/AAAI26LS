package algorithms;

import java.util.List;

public interface LocalSearchTester<S> {
	S getCandidate();
	List<S> getExplore();
}
