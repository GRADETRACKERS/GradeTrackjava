package gradetrack.model;

import gradetrack.exception.DuplicateEntryException;
import gradetrack.exception.InvalidInputException;
import gradetrack.logic.GradeCalculator;
import gradetrack.storage.FileManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The collection of all courses (an ArrayList) plus searching, sorting and saving. */
public class GradeBook {
    public static final String SORT_ADDED = "Order added";
    public static final String SORT_NAME = "Name (A-Z)";
    public static final String SORT_GRADE = "Grade (high to low)";

    private final List<Course> courses = new ArrayList<Course>();
    private final File file;

    public GradeBook(File file) {
        this.file = file;
    }

    /** Loads the saved file, or the sample courses on the very first run. */
    public void load() throws IOException {
        courses.clear();
        if (file.exists()) {
            courses.addAll(FileManager.load(file));
        } else {
            addSamples();
        }
    }

    public void save() throws IOException {
        FileManager.save(courses, file);
    }

    public List<Course> getCourses() {
        return Collections.unmodifiableList(courses);
    }

    public void addCourse(Course c) throws DuplicateEntryException {
        if (findByName(c.getName()) != null) {
            throw new DuplicateEntryException("That course already exists.", c.getName());
        }
        courses.add(c);
    }

    public void removeCourse(Course c) {
        courses.remove(c);
    }

    public Course findById(String id) {
        for (Course c : courses) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public Course findByName(String name) {
        for (Course c : courses) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** SEARCH by name, then SORT by name or by current grade. */
    public List<Course> search(String keyword, String sortMode) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Course> out = new ArrayList<Course>();
        for (Course c : courses) {
            if (k.isEmpty() || c.getName().toLowerCase().contains(k)) {
                out.add(c);
            }
        }
        if (SORT_GRADE.equals(sortMode)) {
            Collections.sort(out, new Comparator<Course>() {
                @Override
                public int compare(Course a, Course b) {
                    Double ga = GradeCalculator.stats(a).grade;
                    Double gb = GradeCalculator.stats(b).grade;
                    if (ga == null && gb == null) {
                        return 0;
                    }
                    if (ga == null) {
                        return 1;     // courses without a grade go last
                    }
                    if (gb == null) {
                        return -1;
                    }
                    return Double.compare(gb, ga);
                }
            });
        } else if (SORT_NAME.equals(sortMode)) {
            Collections.sort(out, new Comparator<Course>() {
                @Override
                public int compare(Course a, Course b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
        }
        return out;   // SORT_ADDED keeps the order in which the courses were created
    }

    private void addSamples() {
        try {
            Course physics = new Course("Physics 101", 4);
            physics.setWeight("Quiz", 20);
            physics.setWeight("Test", 20);
            physics.setWeight("Examination", 40);
            physics.setWeight("Laboratory", 20);
            physics.addAssessment(new Quiz("Quiz 1", 18.0, 20));
            physics.addAssessment(new Quiz("Quiz 2", 16.0, 20));
            physics.addAssessment(new Test("Test 1", 42.0, 50));
            physics.addAssessment(new Laboratory("Lab 1", 45.0, 50));
            physics.addAssessment(new Examination("Final exam", 100));   // overloaded constructor: not graded yet

            Course calculus = new Course("Calculus II", 3);
            calculus.addAssessment(new Quiz("Quiz 1", 45.0, 50));
            calculus.addAssessment(new Test("Test 1", 83.0, 100));
            calculus.addAssessment(new Examination("Final exam", 100));

            Course programming = new Course("Intro to Programming", 3);
            programming.addAssessment(new Quiz("Quiz 1", 90.0, 100));
            programming.addAssessment(new Test("Test 1", 85.0, 100));
            programming.addAssessment(new Examination("Final exam", 89.0, 100));

            courses.add(physics);
            courses.add(calculus);
            courses.add(programming);
        } catch (InvalidInputException e) {
            throw new IllegalStateException("Sample data is invalid", e);
        }
    }
}
