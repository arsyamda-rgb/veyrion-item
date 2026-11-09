package dev.veyrion.item.pack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Private-use glyphs that move the text cursor left/right by N pixels (for HUDs and GUI backgrounds). */
public final class Spaces {
    private Spaces() {}

    public static final int NEG_BASE = 0xF800; // -(2^i)
    public static final int POS_BASE = 0xF810; // +(2^i)

    public static Map<String, Object> advances() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i <= 9; i++) {
            m.put(String.valueOf((char) (NEG_BASE + i)), -(1 << i));
            m.put(String.valueOf((char) (POS_BASE + i)), (1 << i));
        }
        return m;
    }

    /** A string that shifts the cursor by px pixels (negative = left). */
    public static String of(int px) {
        int base = px < 0 ? NEG_BASE : POS_BASE;
        int a = Math.abs(px);
        StringBuilder sb = new StringBuilder();
        while (a >= 512) {
            sb.append((char) (base + 9));
            a -= 512;
        }
        for (int i = 8; i >= 0; i--) {
            if ((a & (1 << i)) != 0) sb.append((char) (base + i));
        }
        return sb.toString();
    }
}
