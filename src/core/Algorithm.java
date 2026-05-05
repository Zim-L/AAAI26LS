package core;

import java.io.Serializable;

/**
 * Interface representing an algorithm
 * @author Antonio J. Nebro
 * @version 0.1
 * @param <Result> Result
 */
public interface Algorithm<Result> extends Runnable, Serializable {
	void run() ;
	Result getResult() ;
	public String getDescription();
}
