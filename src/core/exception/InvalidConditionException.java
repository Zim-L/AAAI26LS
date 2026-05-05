package core.exception;

@SuppressWarnings("serial")
public class InvalidConditionException extends RuntimeException {
  public InvalidConditionException(String message) {
    super(message) ;
  }
}
