package gradetrack.exception;

/** Thrown when a course, a score or a web address that was asked for does not exist. */
public class NotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
