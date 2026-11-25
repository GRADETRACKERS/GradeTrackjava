package gradetrack.logic;

import gradetrack.exception.InvalidInputException;
import gradetrack.exception.TargetTooLowException;
import gradetrack.model.Course;

/** All grade calculations. Static methods that return small result objects. */
public final class GradeCalculator {

    private GradeCalculator() { }

    /** The running grade of a course. */
    public static class Stats {
        public final double gradedWeight;   // weight of the categories that already have scores
        public final double totalWeight;    // weight of all categories
        public final double earned;         // points earned out of totalWeight
        public final Double grade;          // percent over the graded part, null when nothing is graded

        Stats(double gradedWeight, double totalWeight, double earned) {
            this.gradedWeight = gradedWeight;
            this.totalWeight = totalWeight;
            this.earned = earned;
            this.grade = gradedWeight > 0 ? Double.valueOf(earned / gradedWeight * 100) : null;
        }

        /** How much of the course is already graded, in percent. */
        public double gradedPercent() {
            return totalWeight > 0 ? gradedWeight / totalWeight * 100 : 0;
        }
    }

    public static Stats stats(Course c) {
        double gw = 0, tw = 0, earned = 0;
        for (String cat : c.categories()) {
            double w = c.getWeight(cat);
            double[] t = c.totals(cat);
            tw += w;
            if (t[1] > 0) {
                gw += w;
                earned += t[0] / t[1] * w;
            }
        }
        return new Stats(gw, tw, earned);
    }

    public enum Kind { ALL_GRADED, SECURED, IMPOSSIBLE, NEEDED }

    /** The answer to "what do I need on the rest of the course?". */
    public static class TargetResult {
        public final Kind kind;
        public final double needed;       // average % needed on the ungraded work
        public final double earned;       // points earned so far
        public final double totalWeight;
        public final double ungraded;     // % of the course still ungraded
        public final double bestPossible; // final % if every remaining item is perfect

        TargetResult(Kind kind, double needed, double earned, double totalWeight, double ungraded, double bestPossible) {
            this.kind = kind;
            this.needed = needed;
            this.earned = earned;
            this.totalWeight = totalWeight;
            this.ungraded = ungraded;
            this.bestPossible = bestPossible;
        }
    }

    /**
     * @throws TargetTooLowException when the target is below the passing mark
     * @throws InvalidInputException for any other unusable target or a course without weights
     */
    public static TargetResult target(Course c, double target) throws InvalidInputException {
        if (Double.isNaN(target)) {
            throw new InvalidInputException("Enter a target grade to see what you need.");
        }
        if (target < GradeScale.PASSING_MARK) {
            throw new TargetTooLowException(GradeScale.PASSING_MARK);
        }
        if (target > 100) {
            throw new InvalidInputException("Target grade can\u2019t be more than 100.");
        }
        Stats s = stats(c);
        if (s.totalWeight <= 0) {
            throw new InvalidInputException("Set the category weights for this course first.");
        }
        double remaining = s.totalWeight - s.gradedWeight;
        double ungraded = remaining / s.totalWeight * 100;
        double best = (s.earned + remaining) / s.totalWeight * 100;
        if (remaining <= 0) {
            return new TargetResult(Kind.ALL_GRADED, 0, s.earned, s.totalWeight, 0, s.earned / s.totalWeight * 100);
        }
        double need = (target / 100 * s.totalWeight - s.earned) / remaining * 100;
        Kind kind = need <= 0 ? Kind.SECURED : need > 100 ? Kind.IMPOSSIBLE : Kind.NEEDED;
        return new TargetResult(kind, need, s.earned, s.totalWeight, ungraded, best);
    }
}
