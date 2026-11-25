package gradetrack.server;

import gradetrack.exception.DuplicateEntryException;
import gradetrack.exception.InvalidInputException;
import gradetrack.exception.NotFoundException;
import gradetrack.logic.Validator;
import gradetrack.service.GradeService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The web server of GradeTrack. It does two jobs:
 *
 *   1. serves the website files (index.html, sw.js, manifest, icons), and
 *   2. answers the JSON calls under /api/ by passing them to {@link GradeService}.
 *
 * It uses only the JDK's built-in HTTP server, so nothing needs to be downloaded.
 *
 * API summary (every answer is JSON; errors look like {"error": "message"}):
 *   GET    /api/health
 *   GET    /api/state?q=&sort=added|name|grade
 *   POST   /api/courses                             {name, credits}
 *   DELETE /api/courses/{id}
 *   GET    /api/courses/{id}/items?q=&sort=entry|name|type|score
 *   POST   /api/courses/{id}/items                  {name, type, custom, score, max}
 *   PUT    /api/courses/{id}/items/{itemId}         {score}
 *   DELETE /api/courses/{id}/items/{itemId}
 *   PUT    /api/courses/{id}/weights                {category, weight}
 *   GET    /api/courses/{id}/type-check?type=
 *   GET    /api/target?course={id}&target=85
 *   GET    /api/gpa
 */
public class ApiServer {
    private static final Pattern PUBLIC_FILE =
            Pattern.compile("/(index\\.html|sw\\.js|manifest\\.webmanifest|icons/[A-Za-z0-9_.\\-]+)");

    private final HttpServer server;
    private final GradeService service;
    private final File webRoot;

    public ApiServer(int port, GradeService service, File webRoot) throws IOException {
        this.service = service;
        this.webRoot = webRoot;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    // ------------------------------------------------------------------ the API

    private void handleApi(HttpExchange ex) throws IOException {
        int status = 200;
        Object body;
        try {
            body = route(ex);
            if ("POST".equals(ex.getRequestMethod())) {
                status = 201;
            }
        } catch (NotFoundException e) {
            status = 404;
            body = error(e.getMessage());
        } catch (DuplicateEntryException e) {          // must come before InvalidInputException (its parent)
            status = 409;
            Map<String, Object> m = error(e.getMessage());
            m.put("existing", e.getExisting());
            body = m;
        } catch (InvalidInputException e) {
            status = 400;
            body = error(e.getMessage());
        } catch (IOException e) {
            status = 500;
            body = error("The data could not be saved: " + e.getMessage());
        } catch (RuntimeException e) {
            status = 500;
            body = error("Something went wrong on the server.");
        }
        byte[] bytes = Json.stringify(body).getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Object route(HttpExchange ex)
            throws NotFoundException, InvalidInputException, IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath().substring("/api/".length());
        String[] p = path.isEmpty() ? new String[0] : path.split("/");
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

        if (method.equals("GET") && is(p, "health")) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ok", Boolean.TRUE);
            m.put("app", "GradeTrack");
            m.put("backend", "Java " + System.getProperty("java.version"));
            return m;
        }
        if (method.equals("GET") && is(p, "state")) {
            return service.state(query.get("q"), query.get("sort"));
        }
        if (method.equals("GET") && is(p, "gpa")) {
            return service.gpa();
        }
        if (method.equals("GET") && is(p, "target")) {
            return service.target(query.get("course"), query.get("target"));
        }
        if (p.length >= 1 && p[0].equals("courses")) {
            return routeCourses(ex, method, p, query);
        }
        throw new NotFoundException("Unknown address: /api/" + path);
    }

    private Object routeCourses(HttpExchange ex, String method, String[] p, Map<String, String> query)
            throws NotFoundException, InvalidInputException, IOException {
        if (p.length == 1 && method.equals("POST")) {
            Map<String, Object> b = readBody(ex);
            return service.addCourse(Validator.asText(b.get("name")), b.get("credits"));
        }
        if (p.length == 2 && method.equals("DELETE")) {
            return service.deleteCourse(p[1]);
        }
        if (p.length == 3 && p[2].equals("items") && method.equals("GET")) {
            return service.items(p[1], query.get("q"), query.get("sort"));
        }
        if (p.length == 3 && p[2].equals("items") && method.equals("POST")) {
            Map<String, Object> b = readBody(ex);
            return service.addItem(p[1], Validator.asText(b.get("type")), Boolean.TRUE.equals(b.get("custom")),
                    Validator.asText(b.get("name")), b.get("score"), b.get("max"));
        }
        if (p.length == 4 && p[2].equals("items") && method.equals("PUT")) {
            return service.setScore(p[1], p[3], readBody(ex).get("score"));
        }
        if (p.length == 4 && p[2].equals("items") && method.equals("DELETE")) {
            return service.deleteItem(p[1], p[3]);
        }
        if (p.length == 3 && p[2].equals("weights") && method.equals("PUT")) {
            Map<String, Object> b = readBody(ex);
            return service.setWeight(p[1], Validator.asText(b.get("category")), b.get("weight"));
        }
        if (p.length == 3 && p[2].equals("type-check") && method.equals("GET")) {
            return service.typeCheck(p[1], query.get("type"));
        }
        throw new NotFoundException("Unknown address or method.");
    }

    private static boolean is(String[] p, String name) {
        return p.length == 1 && p[0].equals(name);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", message);
        return m;
    }

    private static Map<String, Object> readBody(HttpExchange ex) throws IOException, InvalidInputException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = ex.getRequestBody()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > 100000) {
                    throw new InvalidInputException("The request is too large.");
                }
            }
        }
        return Json.parseObject(new String(buf.toByteArray(), "UTF-8"));
    }

    private static Map<String, String> parseQuery(String raw) throws IOException {
        Map<String, String> m = new HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            m.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
        }
        return m;
    }

    // ------------------------------------------------------------------ the website files

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        File file = PUBLIC_FILE.matcher(path).matches() ? new File(webRoot, path.substring(1)) : null;
        if (file == null || !file.isFile() || !"GET".equals(ex.getRequestMethod())) {
            byte[] msg = "Not found".getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(msg);
            }
            return;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().set("Content-Type", contentType(path));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (path.endsWith(".webmanifest")) {
            return "application/manifest+json; charset=utf-8";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
