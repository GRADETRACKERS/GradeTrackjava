package gradetrack.exception;

/** Thrown when something that already exists (a course, an activity type) is added again. */
public class DuplicateEntryException extends InvalidInputException {
    private static final long serialVersionUID = 1L;
    private final String existing;

    public DuplicateEntryException(String message, String existing) {
        super(message);
        this.existing = existing;
    }

    /** The entry that already exists. */
    public String getExisting() {
        return existing;
    }
}
