package dev.veyrion.item.imports;

/** Converts legacy color codes (&c, §c, &#rrggbb) to MiniMessage tags. Pure Java. */
public final class Legacy {
    private Legacy() {}

    private static final String[] COLORS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    public static String toMiniMessage(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = s.length(), i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < n) {
                char d = Character.toLowerCase(s.charAt(i + 1));
                if (d == '#' && i + 8 <= n && isHex(s, i + 2, i + 8)) {
                    sb.append("<reset><#").append(s, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                String tag = tag(d);
                if (tag != null) { sb.append(tag); i += 2; continue; }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String tag(char d) {
        int idx = "0123456789abcdef".indexOf(d);
        if (idx >= 0) return "<reset><" + COLORS[idx] + ">"; // a color code resets formatting in legacy text
        return switch (d) {
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }
}
