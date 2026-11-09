package dev.veyrion.item.pack;

import java.util.ArrayList;
import java.util.List;

/** Note-block state pool used for custom blocks. "harp" is skipped: it is the state of a freshly placed vanilla note block. */
public final class BlockStates {
    private BlockStates() {}

    public static final List<String> INSTRUMENTS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime",
            "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling",
            "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head");

    public static final List<String> POOL = pool();

    private static List<String> pool() {
        List<String> l = new ArrayList<>(INSTRUMENTS);
        l.remove("harp");
        return List.copyOf(l);
    }

    public static int capacity() {
        return POOL.size() * 25;
    }

    public static String instrument(int state) {
        return POOL.get(state / 25);
    }

    public static int note(int state) {
        return state % 25;
    }
}
