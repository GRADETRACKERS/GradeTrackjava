package gradetrack.storage;

import gradetrack.exception.InvalidInputException;
import gradetrack.model.Assessment;
import gradetrack.model.Course;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FILE HANDLING: saves and loads every course in a plain text file.
 * Each line has a tag and fields separated by "|"; every field is URL-encoded so that
 * names can contain any character.
 *
 *   COURSE|name|credits
 *   WEIGHT|category|percent
 *   ITEM|category|name|score (or -)|max
 */
public final class FileManager {

    private FileManager() { }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String dec(String s) throws IOException {
        return URLDecoder.decode(s, "UTF-8");
    }

    /** Writes all courses to the file, creating the folder if needed. */
    public static void save(List<Course> courses, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create the folder " + parent);
        }
        // try-with-resources closes the writer even when something goes wrong
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            for (Course c : courses) {
                out.write("COURSE|" + enc(c.getName()) + "|" + c.getCredits());
                out.newLine();
                for (Map.Entry<String, Double> w : c.getWeights().entrySet()) {
                    out.write("WEIGHT|" + enc(w.getKey()) + "|" + w.getValue());
                    out.newLine();
                }
                for (Assessment a : c.getItems()) {
                    String score = a.isGraded() ? String.valueOf(a.getScore()) : "-";
                    out.write("ITEM|" + enc(a.getCategory()) + "|" + enc(a.getName()) + "|" + score + "|" + a.getMax());
                    out.newLine();
                }
            }
        }
    }

    /**
     * Reads the courses back. A damaged line is skipped instead of crashing the whole program.
     *
     * @throws IOException when the file cannot be read at all
     */
    public static List<Course> load(File file) throws IOException {
        List<Course> courses = new ArrayList<Course>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            Course current = null;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                try {
                    if (p[0].equals("COURSE")) {
                        current = new Course(dec(p[1]), Double.parseDouble(p[2]));
                        courses.add(current);
                    } else if (p[0].equals("WEIGHT") && current != null) {
                        current.setWeight(dec(p[1]), Double.parseDouble(p[2]));
                    } else if (p[0].equals("ITEM") && current != null) {
                        Double score = p[3].equals("-") ? null : Double.valueOf(p[3]);
                        current.addAssessment(dec(p[1]), dec(p[2]), score, Double.parseDouble(p[4]));
                    }
                } catch (InvalidInputException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    // damaged line: skip it and keep reading
                }
            }
        } finally {
            if (in != null) {
                in.close();   // finally: always release the file
            }
        }
        return courses;
    }
}
