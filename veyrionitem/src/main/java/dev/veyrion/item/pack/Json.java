package dev.veyrion.item.pack;

import java.util.List;
import java.util.Map;

/** Tiny JSON writer so the pack module has zero dependencies. Non-ASCII is escaped as \\uXXXX. */
public final class Json {
    private Json() {}

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        w(sb, o, 0);
        return sb.append('\n').toString();
    }

    /** Minimal parser (objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Long/Double). Throws IllegalArgumentException. */
    public static Object parse(String text) {
        P p = new P(text);
        try {
            Object v = p.value();
            p.ws();
            if (p.i != text.length()) throw new IllegalArgumentException("unexpected data at " + p.i);
            return v;
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("unexpected end of input");
        }
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("expected '" + c + "' at " + i);
            i++;
        }
        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return num();
        }
        Map<String, Object> obj() {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            expect('{'); ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = str(); ws(); expect(':');
                m.put(k, value()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect('}'); return m;
            }
        }
        List<Object> arr() {
            List<Object> l = new java.util.ArrayList<>();
            expect('['); ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect(']'); return l;
            }
        }
        String str() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e); // \" \\ \/
                }
            }
        }
        Object num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalArgumentException("unexpected character '" + s.charAt(i) + "' at " + i);
            String t = s.substring(st, i);
            try { return t.matches("-?\\d+") ? (Object) Long.parseLong(t) : (Object) Double.parseDouble(t); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("bad number " + t); }
        }
    }

    private static void indent(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("  ");
    }

    @SuppressWarnings("unchecked")
    private static void w(StringBuilder sb, Object o, int d) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            quote(sb, s);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                indent(sb, d + 1);
                quote(sb, e.getKey());
                sb.append(": ");
                w(sb, e.getValue(), d + 1);
                if (++i < m.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, d);
            sb.append('}');
        } else if (o instanceof List<?> l) {
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                indent(sb, d + 1);
                w(sb, l.get(i), d + 1);
                if (i < l.size() - 1) sb.append(',');
                sb.append('\n');
            }
            indent(sb, d);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON type: " + o.getClass());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> {
                    if (c < 32 || c > 126) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
