package gradetrack.model;

import gradetrack.exception.InvalidInputException;

/** INHERITANCE: a built-in kind of assessment. */
public class Test extends Assessment {

    public Test(String name, Double score, double max) throws InvalidInputException {
        super(name, score, max);
    }

    /** CONSTRUCTOR OVERLOADING: an assessment that has not been graded yet. */
    public Test(String name, double max) throws InvalidInputException {
        super(name, null, max);
    }

    @Override
    public String getCategory() {
        return "Test";
    }
}
