package lms;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny JSON writer + flat-object reader, so the project needs no extra libraries. */
public class Json {

    // ------------------------------------------------------------ writing
    public static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof BigDecimal) {
            sb.append(((BigDecimal) o).toPlainString());
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) o) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            quote(sb, o.toString());   // String, java.sql.Date, Timestamp ...
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ reading
    /** Parses a flat JSON object like {"a":"x","b":5}. All values come back as Strings. */
    public static Map<String, String> parseObject(String s) {
        Map<String, String> map = new LinkedHashMap<>();
        if (s == null || s.trim().isEmpty()) return map;
        int[] p = {0};
        skipWs(s, p);
        expect(s, p, '{');
        skipWs(s, p);
        if (peek(s, p) == '}') return map;
        while (true) {
            skipWs(s, p);
            String key = readString(s, p);
            skipWs(s, p);
            expect(s, p, ':');
            skipWs(s, p);
            String value;
            if (peek(s, p) == '"') {
                value = readString(s, p);
            } else {
                int start = p[0];
                while (p[0] < s.length() && ",}".indexOf(s.charAt(p[0])) < 0) p[0]++;
                value = s.substring(start, p[0]).trim();
                if (value.equals("null")) value = null;
            }
            map.put(key, value);
            skipWs(s, p);
            char c = peek(s, p);
            p[0]++;
            if (c == '}') break;
            if (c != ',') throw new IllegalArgumentException("Invalid JSON");
        }
        return map;
    }

    private static void skipWs(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static char peek(String s, int[] p) {
        if (p[0] >= s.length()) throw new IllegalArgumentException("Invalid JSON");
        return s.charAt(p[0]);
    }

    private static void expect(String s, int[] p, char c) {
        if (peek(s, p) != c) throw new IllegalArgumentException("Invalid JSON");
        p[0]++;
    }

    private static String readString(String s, int[] p) {
        expect(s, p, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek(s, p);
            p[0]++;
            if (c == '"') break;
            if (c == '\\') {
                char n = peek(s, p);
                p[0]++;
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (p[0] + 4 > s.length()) throw new IllegalArgumentException("Invalid JSON");
                        sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16));
                        p[0] += 4;
                        break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
