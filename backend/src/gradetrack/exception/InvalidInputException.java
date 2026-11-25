package gradetrack.exception;

/**
 * Checked exception for any input the user (or a data file) gives that the system cannot accept.
 * The message is written for the end user, so the UI can show it as-is.
 */
public class InvalidInputException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}
