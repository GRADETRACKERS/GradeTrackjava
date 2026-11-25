package gradetrack.server;

import gradetrack.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader and writer, so the server needs no outside library.
 *
 * Reading gives: Map (object), List (array), String, Double, Boolean or null.
 * Writing accepts the same types (plus any Number).
 */
public final class Json {

    private Json() { }

    // ---------------------------------------------------------------- writing

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);            // 3.0 is written as 3
            } else {
                sb.append(d);
            }
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- reading

    /** Reads a JSON text. An empty text gives an empty object. */
    public static Object parse(String text) throws InvalidInputException {
        if (text == null || text.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        Reader r = new Reader(text);
        Object v = r.value();
        r.skip();
        if (r.pos != text.length()) {
            throw r.fail();
        }
        return v;
    }

    /** Reads a JSON text that must be an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) throws InvalidInputException {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new InvalidInputException("The request must be a JSON object.");
        }
        return (Map<String, Object>) v;
    }

    private static final class Reader {
        private final String s;
        private int pos = 0;

        Reader(String s) {
            this.s = s;
        }

        InvalidInputException fail() {
            return new InvalidInputException("The request is not valid JSON.");
        }

        void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() throws InvalidInputException {
            skip();
            if (pos >= s.length()) {
                throw fail();
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return object();
            } else if (c == '[') {
                return array();
            } else if (c == '"') {
                return string();
            } else if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            } else if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return number();
        }

        Map<String, Object> object() throws InvalidInputException {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            pos++;                      // {
            skip();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                skip();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw fail();
                }
                String key = string();
                skip();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw fail();
                }
                pos++;
                m.put(key, value());
                skip();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == '}') {
                    pos++;
                    return m;
                } else {
                    throw fail();
                }
            }
        }

        List<Object> array() throws InvalidInputException {
            List<Object> l = new ArrayList<Object>();
            pos++;                      // [
            skip();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                l.add(value());
                skip();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == ']') {
                    pos++;
                    return l;
                } else {
                    throw fail();
                }
            }
        }

        String string() throws InvalidInputException {
            StringBuilder sb = new StringBuilder();
            pos++;                      // opening quote
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw fail();
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw fail();
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw fail();
                        }
                        pos += 4;
                        break;
                    default: sb.append(e);      // \" \\ \/
                }
            }
            throw fail();               // the text ended inside a string
        }

        Double number() throws InvalidInputException {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw fail();
            }
            try {
                return Double.valueOf(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw fail();
            }
        }
    }
}
