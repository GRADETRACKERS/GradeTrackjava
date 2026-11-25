package gradetrack.service;

import gradetrack.exception.DuplicateEntryException;
import gradetrack.exception.InvalidInputException;
import gradetrack.exception.NotFoundException;
import gradetrack.logic.GradeCalculator;
import gradetrack.logic.GradeScale;
import gradetrack.logic.TextUtil;
import gradetrack.logic.Validator;
import gradetrack.model.Assessment;
import gradetrack.model.Course;
import gradetrack.model.GradeBook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The application layer. The web server only translates HTTP into calls on this class;
 * every rule (validation, calculation, searching, sorting, saving) lives here or in the
 * model/logic packages. Each method returns plain Maps and Lists, ready to be written as JSON.
 *
 * Methods are synchronized because the server may handle two requests at the same time.
 */
public class GradeService {
    private final GradeBook book;

    public GradeService(GradeBook book) {
        this.book = book;
    }

    // ------------------------------------------------------------------ reading

    /** All courses (searched and sorted when asked), each with its computed grade. */
    public synchronized Map<String, Object> state(String keyword, String sort) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("courses", courseList(keyword, sort));
        return out;
    }

    /** The scores of one course, searched by name/type and sorted. */
    public synchronized Map<String, Object> items(String courseId, String keyword, String sort)
            throws NotFoundException {
        Course c = course(courseId);
        List<Object> list = new ArrayList<Object>();
        for (Assessment a : c.searchItems(keyword, sort)) {
            list.add(itemMap(a));
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("items", list);
        return out;
    }

    /** Is the typed activity type already in the list (ignoring capitals and small typos)? */
    public synchronized Map<String, Object> typeCheck(String courseId, String type) throws NotFoundException {
        Course c = course(courseId);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("similar", TextUtil.findSimilar(type, c.categories()));
        return out;
    }

    /** What average is needed on the remaining work to reach the target? */
    public synchronized Map<String, Object> target(String courseId, String targetText)
            throws NotFoundException, InvalidInputException {
        Course c = course(courseId);
        double target = Validator.numberOrNaN(targetText);
        GradeCalculator.TargetResult r = GradeCalculator.target(c, target);   // may throw TargetTooLowException
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("kind", r.kind.name());
        out.put("target", target);
        out.put("needed", r.needed);
        out.put("earned", r.earned);
        out.put("totalWeight", r.totalWeight);
        out.put("ungraded", r.ungraded);
        out.put("bestPossible", r.bestPossible);
        return out;
    }

    /** The GPA weighted by credits, with one row per course. */
    public synchronized Map<String, Object> gpa() {
        List<Object> rows = new ArrayList<Object>();
        for (Course c : book.getCourses()) {
            Double grade = GradeCalculator.stats(c).grade;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", c.getName());
            row.put("credits", c.getCredits());
            row.put("percent", grade);
            row.put("points", grade == null ? null : Double.valueOf(GradeScale.points(grade)));
            row.put("description", grade == null ? null : GradeScale.describe(grade));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("gpa", GradeScale.gpa(book.getCourses()));   // NaN is written as null
        out.put("rows", rows);
        return out;
    }

    // ------------------------------------------------------------------ changing

    public synchronized Map<String, Object> addCourse(String name, Object credits)
            throws InvalidInputException, IOException {
        if (Validator.asText(name).trim().isEmpty()) {
            throw new InvalidInputException("Enter a subject name.");
        }
        double cr = Validator.requireNumber(Validator.asText(credits), "Credits must be 0 or more.");
        Course c = new Course(name, cr);
        book.addCourse(c);                       // throws DuplicateEntryException
        persist();
        Map<String, Object> out = state(null, GradeBook.SORT_ADDED);
        out.put("created", c.getId());
        return out;
    }

    public synchronized Map<String, Object> deleteCourse(String courseId) throws NotFoundException, IOException {
        book.removeCourse(course(courseId));
        persist();
        return state(null, GradeBook.SORT_ADDED);
    }

    /**
     * Adds a score. When {@code custom} is true the type was typed by the student ("Other (specify)"),
     * so it is first checked against the types that already exist.
     */
    public synchronized Map<String, Object> addItem(String courseId, String type, boolean custom,
                                                   String name, Object score, Object max)
            throws NotFoundException, InvalidInputException, IOException {
        Course c = course(courseId);
        String category = type;
        if (custom) {
            String existing = TextUtil.findSimilar(type, c.categories());
            if (existing != null) {
                throw new DuplicateEntryException(
                        "\u201c" + existing + "\u201d is already in the list. Choose it from the Type dropdown instead.",
                        existing);
            }
            category = TextUtil.clean(type);
        }
        String scoreText = Validator.asText(score);
        Double sc = scoreText.trim().isEmpty() ? null : Double.valueOf(Validator.numberOrNaN(scoreText));
        // NaN values are rejected by the model's own setters, which supply the message
        c.addAssessment(category, name, sc, Validator.numberOrNaN(Validator.asText(max)));   // overloaded method
        persist();
        return state(null, GradeBook.SORT_ADDED);
    }

    public synchronized Map<String, Object> setScore(String courseId, String itemId, Object score)
            throws NotFoundException, InvalidInputException, IOException {
        Assessment a = item(course(courseId), itemId);
        String text = Validator.asText(score);
        a.setScore(text.trim().isEmpty() ? null : Double.valueOf(Validator.numberOrNaN(text)));
        persist();
        return state(null, GradeBook.SORT_ADDED);
    }

    public synchronized Map<String, Object> deleteItem(String courseId, String itemId)
            throws NotFoundException, IOException {
        Course c = course(courseId);
        c.removeAssessment(item(c, itemId));
        persist();
        return state(null, GradeBook.SORT_ADDED);
    }

    public synchronized Map<String, Object> setWeight(String courseId, String category, Object weight)
            throws NotFoundException, InvalidInputException, IOException {
        Course c = course(courseId);
        String match = null;
        for (String cat : c.categories()) {
            if (cat.equalsIgnoreCase(Validator.asText(category))) {
                match = cat;
            }
        }
        if (match == null) {
            throw new NotFoundException("This course has no category called \u201c" + Validator.asText(category) + "\u201d.");
        }
        c.setWeight(match, Validator.requireNumber(Validator.asText(weight), "Weight must be 0 or more."));
        persist();
        return state(null, GradeBook.SORT_ADDED);
    }

    // ------------------------------------------------------------------ helpers

    private void persist() throws IOException {
        book.save();
    }

    private Course course(String id) throws NotFoundException {
        Course c = book.findById(id);
        if (c == null) {
            throw new NotFoundException("That course no longer exists.");
        }
        return c;
    }

    private Assessment item(Course c, String id) throws NotFoundException {
        Assessment a = c.findItem(id);
        if (a == null) {
            throw new NotFoundException("That score no longer exists.");
        }
        return a;
    }

    private List<Object> courseList(String keyword, String sort) {
        String mode = "name".equals(sort) ? GradeBook.SORT_NAME
                : "grade".equals(sort) ? GradeBook.SORT_GRADE : GradeBook.SORT_ADDED;
        List<Object> list = new ArrayList<Object>();
        for (Course c : book.search(keyword, mode)) {
            list.add(courseMap(c));
        }
        return list;
    }

    private Map<String, Object> courseMap(Course c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("credits", c.getCredits());

        Map<String, Object> weights = new LinkedHashMap<String, Object>();
        Map<String, Object> totals = new LinkedHashMap<String, Object>();
        for (String cat : c.categories()) {
            weights.put(cat, c.getWeight(cat));
            double[] t = c.totals(cat);
            Map<String, Object> tm = new LinkedHashMap<String, Object>();
            tm.put("score", t[0]);
            tm.put("max", t[1]);
            totals.put(cat, tm);
        }
        m.put("categories", new ArrayList<String>(c.categories()));
        m.put("weights", weights);
        m.put("totals", totals);

        GradeCalculator.Stats s = GradeCalculator.stats(c);
        Map<String, Object> st = new LinkedHashMap<String, Object>();
        st.put("gw", s.gradedWeight);
        st.put("tw", s.totalWeight);
        st.put("earned", s.earned);
        st.put("grade", s.grade);
        st.put("graded", s.gradedPercent());
        m.put("stats", st);

        List<Object> items = new ArrayList<Object>();
        for (Assessment a : c.getItems()) {
            items.add(itemMap(a));
        }
        m.put("items", items);
        return m;
    }

    private Map<String, Object> itemMap(Assessment a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("type", a.getCategory());      // POLYMORPHISM: each subclass reports its own category
        m.put("score", a.getScore());
        m.put("max", a.getMax());
        m.put("percent", a.isGraded() ? Double.valueOf(a.getPercent()) : null);
        return m;
    }
}
