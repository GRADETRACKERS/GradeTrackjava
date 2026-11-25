package gradetrack.logic;

import gradetrack.model.Course;

import java.util.List;

/** The school grading scale (1.00 is the highest) and the GPA formula. All members are static. */
public final class GradeScale {
    public static final int PASSING_MARK = 75;

    // each row: lowest percent for the grade, the grade, its description
    private static final Object[][] SCALE = {
        {97, 1.00, "Excellent"}, {94, 1.25, "Excellent"}, {91, 1.50, "Very Good"},
        {88, 1.75, "Very Good"}, {85, 2.00, "Good"}, {82, 2.25, "Good"},
        {79, 2.50, "Satisfactory"}, {76, 2.75, "Passing"}, {75, 3.00, "Passing"},
        {0, 5.00, "Failed"}
    };

    private GradeScale() { }

    private static Object[] row(double percent) {
        for (Object[] r : SCALE) {
            if (percent >= (Integer) r[0]) {
                return r;
            }
        }
        return SCALE[SCALE.length - 1];
    }

    public static double points(double percent) {
        return (Double) row(percent)[1];
    }

    public static String describe(double percent) {
        return (String) row(percent)[2];
    }

    /** GPA weighted by credits, or NaN when no course has a grade yet. */
    public static double gpa(List<Course> courses) {
        double totalPoints = 0, totalCredits = 0;
        for (Course c : courses) {
            Double grade = GradeCalculator.stats(c).grade;
            if (grade != null) {
                totalPoints += points(grade) * c.getCredits();
                totalCredits += c.getCredits();
            }
        }
        return totalCredits == 0 ? Double.NaN : totalPoints / totalCredits;
    }
}
