package gradetrack.exception;

/** Thrown when the target final grade is below the passing mark. */
public class TargetTooLowException extends InvalidInputException {
    private static final long serialVersionUID = 1L;

    public TargetTooLowException(int minimum) {
        super("Target grade must be at least " + minimum + " (the passing mark).");
    }
}
