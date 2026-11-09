package dev.veyrion.item.content;

import org.bukkit.Instrument;

import java.util.HashMap;
import java.util.Map;

/** Maps pack blockstate names to Bukkit's Instrument enum and back. */
public final class Instruments {
    private Instruments() {}

    private static final Map<String, Instrument> BY_NAME = new HashMap<>();
    private static final Map<Instrument, String> BY_ENUM = new HashMap<>();

    private static void add(String name, Instrument i) { BY_NAME.put(name, i); BY_ENUM.put(i, name); }

    static {
        add("harp", Instrument.PIANO);
        add("basedrum", Instrument.BASS_DRUM);
        add("snare", Instrument.SNARE_DRUM);
        add("hat", Instrument.STICKS);
        add("bass", Instrument.BASS_GUITAR);
        add("flute", Instrument.FLUTE);
        add("bell", Instrument.BELL);
        add("guitar", Instrument.GUITAR);
        add("chime", Instrument.CHIME);
        add("xylophone", Instrument.XYLOPHONE);
        add("iron_xylophone", Instrument.IRON_XYLOPHONE);
        add("cow_bell", Instrument.COW_BELL);
        add("didgeridoo", Instrument.DIDGERIDOO);
        add("bit", Instrument.BIT);
        add("banjo", Instrument.BANJO);
        add("pling", Instrument.PLING);
        add("zombie", Instrument.ZOMBIE);
        add("skeleton", Instrument.SKELETON);
        add("creeper", Instrument.CREEPER);
        add("dragon", Instrument.DRAGON);
        add("wither_skeleton", Instrument.WITHER_SKELETON);
        add("piglin", Instrument.PIGLIN);
        add("custom_head", Instrument.CUSTOM_HEAD);
    }

    public static Instrument bukkit(String name) { return BY_NAME.get(name); }
    public static String name(Instrument i) { return BY_ENUM.get(i); }
}
