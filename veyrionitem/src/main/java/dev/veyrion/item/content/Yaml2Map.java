package dev.veyrion.item.content;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deep-converts a Bukkit ConfigurationSection into plain Maps/Lists so the importers stay Bukkit-free. */
final class Yaml2Map {
    private Yaml2Map() {}

    static Map<String, Object> convert(ConfigurationSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) m.put(k, value(s.get(k)));
        return m;
    }

    private static Object value(Object o) {
        if (o instanceof ConfigurationSection cs) return convert(cs);
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object e : l) out.add(value(e));
            return out;
        }
        return o;
    }
}
