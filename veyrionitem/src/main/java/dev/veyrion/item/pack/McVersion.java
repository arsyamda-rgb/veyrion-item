package dev.veyrion.item.pack;

import java.util.ArrayList;
import java.util.List;

/** A Minecraft release we can generate a resource pack for. */
public record McVersion(String name, int packFormat) {

    /** Resource pack formats for 1.21.x. Verify against the wiki when new patches ship. */
    public static final List<McVersion> ALL = List.of(
            new McVersion("1.21.1", 34),
            new McVersion("1.21.2", 42),
            new McVersion("1.21.3", 42),
            new McVersion("1.21.4", 46),
            new McVersion("1.21.5", 55),
            new McVersion("1.21.6", 63),
            new McVersion("1.21.7", 64),
            new McVersion("1.21.8", 64),
            new McVersion("1.21.9", 69),
            new McVersion("1.21.10", 69),
            new McVersion("1.21.11", 75));

    /** 1.21.4+ replaced model "overrides" with assets/minecraft/items/*.json definitions. */
    public boolean modernItemModels() {
        return packFormat >= 46;
    }

    /** 1.21.9+ describes the supported range with min_format / max_format. */
    public boolean minMaxFormat() {
        return packFormat >= 69;
    }

    public static McVersion parse(String name) {
        for (McVersion v : ALL) {
            if (v.name.equals(name.trim())) return v;
        }
        throw new IllegalArgumentException("Unknown version: " + name);
    }

    public static List<McVersion> range(String from, String to) {
        int a = ALL.indexOf(parse(from));
        int b = ALL.indexOf(parse(to));
        if (a > b) throw new IllegalArgumentException("from is newer than to");
        return new ArrayList<>(ALL.subList(a, b + 1));
    }
}
