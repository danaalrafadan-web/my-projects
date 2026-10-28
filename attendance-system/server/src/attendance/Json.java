package attendance;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** أداة JSON بسيطة بدون مكتبات خارجية (كائنات مسطحة فقط). */
public final class Json {
    private static final Pattern PAIR = Pattern.compile(
        "\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?[\\d.]+|true|false|null))");

    private Json() {}

    public static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        Matcher m = PAIR.matcher(json == null ? "" : json);
        while (m.find()) {
            String v = m.group(2) != null ? unescape(m.group(2)) : m.group(3);
            if (!"null".equals(v)) out.put(m.group(1), v);
        }
        return out;
    }

    public static String str(Object o) {
        if (o == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : o.toString().toCharArray()) {
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
        return sb.append('"').toString();
    }

    /** {"k1":"v1","k2":"v2"} من أزواج نصية. */
    public static String obj(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(str(kv[i])).append(':').append(str(kv[i + 1]));
        }
        return sb.append('}').toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4; break;
                default: sb.append(n);
            }
        }
        return sb.toString();
    }
}
