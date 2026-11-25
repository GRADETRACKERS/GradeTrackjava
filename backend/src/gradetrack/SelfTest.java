package gradetrack;

import gradetrack.exception.InvalidInputException;
import gradetrack.exception.TargetTooLowException;
import gradetrack.logic.GradeCalculator;
import gradetrack.logic.GradeScale;
import gradetrack.logic.TextUtil;
import gradetrack.model.Course;
import gradetrack.model.GradeBook;
import gradetrack.server.ApiServer;
import gradetrack.server.Json;
import gradetrack.service.GradeService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Quick checks of the model, the calculations, file saving and the web API. Run:  java -cp out gradetrack.SelfTest */
public class SelfTest {
    private static int passed = 0, failed = 0;

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + what);
        } else {
            failed++;
            System.out.println("FAIL  " + what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.05;
    }

    public static void main(String[] args) throws Exception {
        File tmp = File.createTempFile("gradetrack", ".txt");
        tmp.delete();
        GradeBook book = new GradeBook(tmp);
        book.load();
        Course calc = book.findByName("calculus ii");
        check("sample data loads 3 courses", book.getCourses().size() == 3);
        check("Calculus II current grade is 85.8%", near(GradeCalculator.stats(calc).grade, 85.8));

        GradeCalculator.TargetResult r = GradeCalculator.target(calc, 70 + 5);   // 75 is allowed
        check("target 75 needs about 64.2% on the final", r.kind == GradeCalculator.Kind.NEEDED && near(r.needed, 64.2));
        check("50% of Calculus II is still ungraded", near(r.ungraded, 50));

        try {
            GradeCalculator.target(calc, 74);
            check("target 74 is refused", false);
        } catch (TargetTooLowException e) {
            check("target 74 is refused with the right message",
                    e.getMessage().equals("Target grade must be at least 75 (the passing mark)."));
        }
        try {
            GradeCalculator.target(calc, 60);
            check("target 60 is refused", false);
        } catch (InvalidInputException e) {
            check("target 60 is refused", e instanceof TargetTooLowException);
        }

        calc.addAssessment("Project", "Project 1", 45.0, 50);
        check("custom type 'Project' becomes a category", calc.categories().contains("Project"));
        check("custom type starts with weight 0", calc.getWeight("Project") == 0);
        calc.setWeight("Project", 10);
        check("custom type counts once it has a weight", GradeCalculator.stats(calc).totalWeight == 110);

        List<String> cats = calc.categories();
        check("'Laboratory' is found as existing", "Laboratory".equals(TextUtil.findSimilar("Laboratory", cats)));
        check("typo 'labaratory' is found as existing", "Laboratory".equals(TextUtil.findSimilar("labaratory", cats)));
        check("'QUIZ' is found as existing", "Quiz".equals(TextUtil.findSimilar("QUIZ", cats)));
        check("'Project' is found as existing once added", "Project".equals(TextUtil.findSimilar("project", cats)));
        check("'Seminar' is a new type", TextUtil.findSimilar("Seminar", cats) == null);

        try {
            calc.addAssessment("Quiz", "Bad", 60.0, 50);
            check("score above total is refused", false);
        } catch (InvalidInputException e) {
            check("score above total is refused", true);
        }
        try {
            calc.addAssessment("   ", "Bad", 1.0, 5);
            check("empty type is refused", false);
        } catch (InvalidInputException e) {
            check("empty type is refused", true);
        }

        book.save();
        GradeBook again = new GradeBook(tmp);
        again.load();
        Course back = again.findByName("Calculus II");
        check("file round trip keeps the custom type", back.categories().contains("Project"));
        check("file round trip keeps the grade", near(GradeCalculator.stats(back).grade, GradeCalculator.stats(calc).grade));

        check("GPA scale: 85% is 2.00", GradeScale.points(85) == 2.00);
        check("GPA scale: 74% is 5.00 (failed)", GradeScale.points(74) == 5.00);
        check("GPA of the sample data is a number", !Double.isNaN(GradeScale.gpa(book.getCourses())));
        check("search finds 'calc'", book.search("calc", GradeBook.SORT_NAME).size() == 1);
        check("sort by grade puts best first",
                book.search("", GradeBook.SORT_GRADE).get(0).getName().equals("Intro to Programming"));
        tmp.delete();

        testApi();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ the web API

    /** Starts the real server on a free port and calls it over HTTP, like the website does. */
    private static void testApi() throws Exception {
        File tmp = File.createTempFile("gradetrack-api", ".txt");
        tmp.delete();
        GradeBook book = new GradeBook(tmp);
        book.load();
        ApiServer server = new ApiServer(0, new GradeService(book), new File(new File("index.html").isFile() ? "." : ".."));
        server.start();
        base = "http://localhost:" + server.getPort();
        try {
            Map<String, Object> r = obj(call("GET", "/api/health", null));
            check("API: health answers ok", Boolean.TRUE.equals(r.get("ok")));

            String[] res = call("GET", "/api/state?sort=grade", null);
            Map<String, Object> state = obj(res);
            List<?> courses = (List<?>) state.get("courses");
            check("API: state lists 3 courses", courses.size() == 3);
            check("API: sort=grade puts the best course first",
                    "Intro to Programming".equals(((Map<?, ?>) courses.get(0)).get("name")));
            check("API: search q=calc finds one course",
                    ((List<?>) obj(call("GET", "/api/state?q=calc", null)).get("courses")).size() == 1);

            Map<?, ?> calc = null;
            for (Object o : (List<?>) obj(call("GET", "/api/state?q=calc", null)).get("courses")) {
                calc = (Map<?, ?>) o;
            }
            String calcId = (String) calc.get("id");
            check("API: Calculus II grade is computed by Java (85.8)",
                    near(((Number) ((Map<?, ?>) calc.get("stats")).get("grade")).doubleValue(), 85.8));

            res = call("GET", "/api/target?course=" + calcId + "&target=74", null);
            check("API: target 74 gives 400 with the passing-mark message", res[0].equals("400")
                    && "Target grade must be at least 75 (the passing mark).".equals(obj(res).get("error")));
            res = call("GET", "/api/target?course=" + calcId + "&target=75", null);
            check("API: target 75 needs about 64.2%", res[0].equals("200")
                    && near(((Number) obj(res).get("needed")).doubleValue(), 64.2));

            res = call("POST", "/api/courses", "{\"name\":\"calculus ii\",\"credits\":3}");
            check("API: duplicate course gives 409", res[0].equals("409"));
            res = call("POST", "/api/courses", "{\"name\":\"  \",\"credits\":3}");
            check("API: empty course name gives 400", res[0].equals("400"));
            res = call("POST", "/api/courses", "{\"name\":\"Biology\",\"credits\":2}");
            check("API: new course gives 201 and its id", res[0].equals("201") && obj(res).get("created") != null);
            String bioId = (String) obj(res).get("created");

            res = call("POST", "/api/courses/" + bioId + "/items",
                    "{\"name\":\"Quiz 1\",\"type\":\"Quiz\",\"score\":\"60\",\"max\":\"50\"}");
            check("API: score above total gives 400", res[0].equals("400"));
            res = call("POST", "/api/courses/" + bioId + "/items",
                    "{\"name\":\"Quiz 1\",\"type\":\"Quiz\",\"score\":\"45\",\"max\":\"50\"}");
            check("API: valid score is added", res[0].equals("201"));
            res = call("POST", "/api/courses/" + bioId + "/items",
                    "{\"name\":\"Lab\",\"type\":\"labaratory\",\"custom\":true,\"score\":\"\",\"max\":\"50\"}");
            check("API: typo of an existing type gives 409", res[0].equals("409")
                    && "Laboratory".equals(obj(res).get("existing")));
            res = call("POST", "/api/courses/" + bioId + "/items",
                    "{\"name\":\"P1\",\"type\":\"Project\",\"custom\":true,\"score\":\"\",\"max\":\"50\"}");
            check("API: new custom type is added", res[0].equals("201"));
            res = call("GET", "/api/courses/" + bioId + "/items?q=proj", null);
            check("API: score search finds the Project", ((List<?>) obj(res).get("items")).size() == 1);
            res = call("PUT", "/api/courses/" + bioId + "/weights", "{\"category\":\"Project\",\"weight\":-5}");
            check("API: negative weight gives 400", res[0].equals("400"));
            res = call("PUT", "/api/courses/nope/weights", "{\"category\":\"Quiz\",\"weight\":5}");
            check("API: unknown course gives 404", res[0].equals("404"));
            res = call("POST", "/api/courses", "{not json");
            check("API: broken JSON gives 400", res[0].equals("400"));

            res = call("GET", "/api/gpa", null);
            check("API: GPA is returned", res[0].equals("200") && obj(res).get("gpa") != null);

            GradeBook again = new GradeBook(tmp);
            again.load();
            check("API: changes were saved to the file", again.findByName("Biology") != null);

            res = call("GET", "/", null);
            check("Server: the website is served at /", res[0].equals("200") && res[1].contains("GradeTrack"));
            res = call("GET", "/src/gradetrack/Main.java", null);
            check("Server: source files are not served", res[0].equals("404"));
        } finally {
            server.stop();
            tmp.delete();
        }
    }

    private static String base;

    private static Map<String, Object> obj(String[] response) throws Exception {
        return Json.parseObject(response[1]);
    }

    /** One HTTP call. Returns {status code, body text}. */
    private static String[] call(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(base + path).toURL().openConnection();
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes("UTF-8"));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while (in != null && (n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return new String[] {String.valueOf(code), new String(buf.toByteArray(), "UTF-8")};
    }
}
