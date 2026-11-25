package gradetrack.logic;

import java.util.Collection;

/** Text helpers, including the "is this activity type already there?" check that forgives small typos. */
public final class TextUtil {

    private TextUtil() { }

    /** Trim and collapse repeated spaces. */
    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    /** Edit distance between two strings (number of single-letter changes). */
    public static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[a.length()][b.length()];
    }

    /**
     * Returns the existing name that the typed text matches (ignoring capitals and allowing a small typo,
     * so "labaratory" matches "Laboratory"), or null when it is really a new name.
     */
    public static String findSimilar(String raw, Collection<String> existing) {
        String t = clean(raw).toLowerCase();
        if (t.isEmpty()) {
            return null;
        }
        for (String e : existing) {
            String el = e.toLowerCase();
            int allowed = el.length() >= 7 ? 2 : el.length() >= 4 ? 1 : 0;
            if (el.equals(t) || distance(el, t) <= allowed) {
                return e;
            }
        }
        return null;
    }
}
