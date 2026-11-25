package gradetrack.logic;

import gradetrack.exception.InvalidInputException;

/** Input validation helpers: turn text typed by the user into numbers or throw a clear message. */
public final class Validator {

    private Validator() { }

    /** A required number. The same message is used for blank and for non-numeric text. */
    public static double requireNumber(String text, String message) throws InvalidInputException {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidInputException(message);
        }
        double v;
        try {
            v = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(message);
        }
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new InvalidInputException(message);
        }
        return v;
    }

    /** An optional number: blank gives null (used for "not graded yet"). */
    public static Double optionalNumber(String text, String message) throws InvalidInputException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return requireNumber(text, message);
    }

    /**
     * Turns a value that came from JSON (null, a number, or text) into plain text.
     * A whole number such as 3.0 becomes "3".
     */
    public static String asText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
        }
        return String.valueOf(value);
    }

    /** Lenient parsing: NaN when the text is not a number, so the model's own setters give the message. */
    public static double numberOrNaN(String text) {
        try {
            double v = Double.parseDouble(text == null ? "" : text.trim());
            return Double.isInfinite(v) ? Double.NaN : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
