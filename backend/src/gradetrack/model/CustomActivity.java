package gradetrack.model;

import gradetrack.exception.InvalidInputException;

/** An activity type typed in by the student through "Other (specify)", such as "Project". */
public class CustomActivity extends Assessment {
    private final String category;

    public CustomActivity(String category, String name, Double score, double max) throws InvalidInputException {
        super(name, score, max);
        if (category == null || category.trim().isEmpty()) {
            throw new InvalidInputException("Type in the kind of activity, for example \u201cProject\u201d.");
        }
        this.category = category.trim();
    }

    @Override
    public String getCategory() {
        return category;
    }
}
