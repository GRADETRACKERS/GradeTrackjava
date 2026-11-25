package gradetrack.model;

import gradetrack.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** A subject: its credits, the weight of each category, and all of its assessments. */
public class Course {
    /** STATIC member: the four activity types every course starts with. */
    public static final List<String> BUILT_IN =
            Collections.unmodifiableList(Arrays.asList("Quiz", "Test", "Examination", "Laboratory"));

    /** Sort modes for the scores table. */
    public static final String ITEMS_AS_ADDED = "entry";
    public static final String ITEMS_BY_NAME = "name";
    public static final String ITEMS_BY_TYPE = "type";
    public static final String ITEMS_BY_SCORE = "score";

    /** STATIC member: hands out a new id to every course. */
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final String id = "c" + NEXT_ID.incrementAndGet();
    private String name;
    private double credits;
    private final Map<String, Double> weights = new LinkedHashMap<String, Double>();  // category -> percent
    private final List<Assessment> items = new ArrayList<Assessment>();

    public Course(String name, double credits) throws InvalidInputException {
        setName(name);
        setCredits(credits);
        weights.put("Quiz", 20.0);
        weights.put("Test", 30.0);
        weights.put("Examination", 50.0);
        weights.put("Laboratory", 0.0);
    }

    /** CONSTRUCTOR OVERLOADING: credits default to 3. */
    public Course(String name) throws InvalidInputException {
        this(name, 3);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) throws InvalidInputException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("Enter a subject name.");
        }
        this.name = name.trim();
    }

    public double getCredits() {
        return credits;
    }

    public void setCredits(double credits) throws InvalidInputException {
        if (Double.isNaN(credits) || credits < 0) {
            throw new InvalidInputException("Credits must be 0 or more.");
        }
        this.credits = credits;
    }

    public List<Assessment> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Map<String, Double> getWeights() {
        return Collections.unmodifiableMap(weights);
    }

    /** METHOD OVERLOADING (1 of 2): add an object that was already built. */
    public void addAssessment(Assessment a) {
        items.add(a);
        if (!weights.containsKey(a.getCategory())) {
            weights.put(a.getCategory(), 0.0);   // a new custom type starts at 0% until the student sets it
        }
    }

    /** METHOD OVERLOADING (2 of 2): build the right kind of assessment from raw values. */
    public void addAssessment(String category, String name, Double score, double max) throws InvalidInputException {
        addAssessment(Assessment.create(category, name, score, max));
    }

    public void removeAssessment(Assessment a) {
        items.remove(a);
    }

    /** Finds one assessment by its id, or null. */
    public Assessment findItem(String itemId) {
        for (Assessment a : items) {
            if (a.getId().equals(itemId)) {
                return a;
            }
        }
        return null;
    }

    /** SEARCH the scores by name or type, then SORT them (as added, by name, by type, or best score first). */
    public List<Assessment> searchItems(String keyword, String sortMode) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Assessment> out = new ArrayList<Assessment>();
        for (Assessment a : items) {
            if (k.isEmpty() || a.getName().toLowerCase().contains(k) || a.getCategory().toLowerCase().contains(k)) {
                out.add(a);
            }
        }
        if (ITEMS_BY_NAME.equals(sortMode)) {
            Collections.sort(out, new Comparator<Assessment>() {
                @Override
                public int compare(Assessment a, Assessment b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
        } else if (ITEMS_BY_TYPE.equals(sortMode)) {
            Collections.sort(out, new Comparator<Assessment>() {
                @Override
                public int compare(Assessment a, Assessment b) {
                    return a.getCategory().compareToIgnoreCase(b.getCategory());
                }
            });
        } else if (ITEMS_BY_SCORE.equals(sortMode)) {
            Collections.sort(out, new Comparator<Assessment>() {
                @Override
                public int compare(Assessment a, Assessment b) {
                    if (!a.isGraded() && !b.isGraded()) {
                        return 0;
                    }
                    if (!a.isGraded()) {
                        return 1;     // not graded yet goes last
                    }
                    if (!b.isGraded()) {
                        return -1;
                    }
                    return Double.compare(b.getPercent(), a.getPercent());
                }
            });
        }
        return out;   // ITEMS_AS_ADDED keeps the order in which the scores were entered
    }

    /** Built-in categories first, then every custom category used by this course. */
    public List<String> categories() {
        List<String> out = new ArrayList<String>(BUILT_IN);
        for (Assessment a : items) {
            boolean found = false;
            for (String c : out) {
                if (c.equalsIgnoreCase(a.getCategory())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                out.add(a.getCategory());
            }
        }
        return out;
    }

    public double getWeight(String category) {
        Double w = weights.get(category);
        return w == null ? 0 : w;
    }

    public void setWeight(String category, double weight) throws InvalidInputException {
        if (Double.isNaN(weight) || weight < 0) {
            throw new InvalidInputException("Weight must be 0 or more.");
        }
        weights.put(category, weight);
    }

    /** Total score and total possible points of the graded work in one category: {score, max}. */
    public double[] totals(String category) {
        double score = 0, max = 0;
        for (Assessment a : items) {
            if (a.getCategory().equalsIgnoreCase(category) && a.isGraded()) {
                score += a.getScore();
                max += a.getMax();
            }
        }
        return new double[] {score, max};
    }

    @Override
    public String toString() {
        return name;
    }
}
