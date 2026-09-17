package io.ledger.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal recursive-descent JSON reader, about a hundred lines.
 *
 * <p>Hand-written rather than pulled in, so the jar stays dependency-free and the brief's "no
 * framework" spirit holds. Numbers are read as {@link BigDecimal}, never as double, because this
 * is a money system and a double would defeat the point of the rest of the design.
 */
public final class Json {

    private final String src;
    private int i;

    private Json(String src) { this.src = src; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.src.length()) throw p.fail("trailing content");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("expected an object, got " + kind(o));
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) {
        if (!(o instanceof List)) throw new IllegalArgumentException("expected an array, got " + kind(o));
        return (List<Object>) o;
    }

    public static String str(Map<String, Object> o, String key) {
        Object v = o.get(key);
        if (v == null) throw new IllegalArgumentException("missing \"" + key + "\" in " + o.keySet());
        return String.valueOf(v);
    }

    public static String strOrNull(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static int intAt(Map<String, Object> o, String key) {
        Object v = o.get(key);
        if (v == null) throw new IllegalArgumentException("missing \"" + key + "\" in " + o.keySet());
        return ((BigDecimal) v).intValueExact();
    }

    public static int intOr(Map<String, Object> o, String key, int fallback) {
        Object v = o.get(key);
        return v == null ? fallback : ((BigDecimal) v).intValueExact();
    }

    public static boolean boolOr(Map<String, Object> o, String key, boolean fallback) {
        Object v = o.get(key);
        return v == null ? fallback : (Boolean) v;
    }

    private static String kind(Object o) { return o == null ? "null" : o.getClass().getSimpleName(); }

    // ---- parser -----------------------------------------------------------------------------------------

    private Object value() {
        char c = peek();
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (next() != ':') throw fail("expected ':' after key \"" + k + "\"");
            ws();
            m.put(k, value());
            ws();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw fail("expected ',' or '}' in object");
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = next();
            if (c == ']') return l;
            if (c != ',') throw fail("expected ',' or ']' in array");
        }
    }

    private String string() {
        if (src.charAt(i) != '"') throw fail("expected a string");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = next();
            switch (e) {
                case '"' -> b.append('"');
                case '\\' -> b.append('\\');
                case '/' -> b.append('/');
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    if (i + 4 > src.length()) throw fail("unexpected end of input in \\u escape");
                    b.append((char) Integer.parseInt(src.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw fail("bad escape \\" + e);
            }
        }
    }

    private BigDecimal number() {
        int start = i;
        while (i < src.length() && "+-0123456789.eE".indexOf(src.charAt(i)) >= 0) i++;
        if (start == i) throw fail("expected a value");
        return new BigDecimal(src.substring(start, i));
    }

    private void expect(String word) {
        if (!src.startsWith(word, i)) throw fail("expected " + word);
        i += word.length();
    }

    /** Truncated input used to surface as StringIndexOutOfBoundsException from deep inside. */
    private char next() {
        if (i >= src.length()) throw fail("unexpected end of input");
        return src.charAt(i++);
    }

    private char peek() {
        if (i >= src.length()) throw fail("unexpected end of input");
        return src.charAt(i);
    }

    private void ws() { while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++; }

    private IllegalArgumentException fail(String message) {
        int line = 1;
        for (int k = 0; k < Math.min(i, src.length()); k++) if (src.charAt(k) == '\n') line++;
        return new IllegalArgumentException("JSON error at line " + line + ": " + message);
    }
}