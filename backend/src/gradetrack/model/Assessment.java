package gradetrack.model;

import gradetrack.exception.InvalidInputException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ABSTRACTION: one graded piece of work (a quiz, a test, ...). Every kind of assessment
 * shares the same data and rules, but each subclass decides its own category name.
 * ENCAPSULATION: all fields are private and can only change through validated setters.
 */
public abstract class Assessment {
    /** STATIC member: hands out a new id to every assessment, so the website can point at one of them. */
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final String id = "a" + NEXT_ID.incrementAndGet();
    private String name;
    private Double score;   // null means "not graded yet"
    private double max;

    protected Assessment(String name, Double score, double max) throws InvalidInputException {
        setName(name);
        setMax(max);        // max first, because setScore compares against it
        setScore(score);
    }

    public String getId() {
        return id;
    }

    /** POLYMORPHISM: each subclass answers with its own category. */
    public abstract String getCategory();

    public String getName() {
        return name;
    }

    public void setName(String name) throws InvalidInputException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("Name this score, for example \u201cQuiz 2\u201d.");
        }
        this.name = name.trim();
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) throws InvalidInputException {
        if (Double.isNaN(max) || max <= 0) {
            throw new InvalidInputException("\u201cOut of\u201d must be greater than 0.");
        }
        this.max = max;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) throws InvalidInputException {
        if (score != null) {
            if (score.isNaN() || score < 0) {
                throw new InvalidInputException("Score must be 0 or more, or blank if not graded yet.");
            }
            if (score > max) {
                throw new InvalidInputException("Score can\u2019t be higher than the total.");
            }
        }
        this.score = score;
    }

    public boolean isGraded() {
        return score != null;
    }

    /** Score as a percent of the total, or 0 when not graded. */
    public double getPercent() {
        return score == null ? 0 : score / max * 100;
    }

    /**
     * Static factory: picks the right subclass from the category text.
     * Any category that is not built in becomes a {@link CustomActivity}.
     */
    public static Assessment create(String category, String name, Double score, double max)
            throws InvalidInputException {
        String c = category == null ? "" : category.trim();
        if (c.isEmpty()) {
            throw new InvalidInputException("Type in the kind of activity, for example \u201cProject\u201d.");
        }
        if (c.equalsIgnoreCase("Quiz")) {
            return new Quiz(name, score, max);
        } else if (c.equalsIgnoreCase("Test")) {
            return new Test(name, score, max);
        } else if (c.equalsIgnoreCase("Examination")) {
            return new Examination(name, score, max);
        } else if (c.equalsIgnoreCase("Laboratory")) {
            return new Laboratory(name, score, max);
        }
        return new CustomActivity(c, name, score, max);
    }

    @Override
    public String toString() {
        return name + " (" + getCategory() + ")";
    }
}
