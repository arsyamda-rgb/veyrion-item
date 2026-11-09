package dev.veyrion.item.imports;

import java.util.*;

/**
 * Reads the contents of an ItemsAdder-style config file (already parsed into Maps/Lists) and turns it into
 * VeyrionItem's native sections (items / blocks / furniture / font_images). Pure Java, no Bukkit.
 *
 * Written from the public config format, NOT verified against real files here. Everything it does not
 * understand is reported in Result.notes() instead of being dropped silently.
 */
public final class ItemsAdderImporter {
    private ItemsAdderImporter() {}

    public record Result(Map<String, Object> nativeDoc, String namespace, List<String> notes,
                         int items, int blocks, int furniture, int fonts) {}

    private static final Set<String> NATIVE_PASSTHROUGH = Set.of("blocks", "furniture", "menus");

    // ------------------------------------------------------------ detection

    public static boolean looksLikeItemsAdder(Map<String, Object> doc) {
        Map<String, Object> info = map(doc.get("info"));
        if (info != null && info.get("namespace") != null) return true;
        Map<String, Object> items = map(doc.get("items"));
        if (items != null) {
            for (Object v : items.values()) {
                Map<String, Object> m = map(v);
                if (m != null && map(m.get("resource")) != null) return true;
            }
        }
        Map<String, Object> fonts = map(doc.get("font_images"));
        if (fonts != null) {
            for (Object v : fonts.values()) {
                Map<String, Object> m = map(v);
                if (m != null && m.containsKey("path")) return true;
            }
        }
        return false;
    }

    public static String namespaceOf(Map<String, Object> doc) {
        Map<String, Object> info = map(doc.get("info"));
        return info == null ? null : str(info.get("namespace"), null);
    }

    // ------------------------------------------------------------ conversion

    public static Result convert(Map<String, Object> doc, String label) {
        List<String> notes = new ArrayList<>();
        Map<String, Object> items = new LinkedHashMap<>(), blocks = new LinkedHashMap<>(),
                furniture = new LinkedHashMap<>(), fonts = new LinkedHashMap<>();
        int ci = 0, cb = 0, cf = 0, cfont = 0;

        Map<String, Object> src = map(doc.get("items"));
        if (src != null) {
            for (Map.Entry<String, Object> e : src.entrySet()) {
                String id = e.getKey();
                Map<String, Object> item = map(e.getValue());
                if (item == null) { notes.add(label + ": item '" + id + "' is not a section, skipped"); continue; }
                Map<String, Object> res = map(item.get("resource"));
                if (res == null) { items.put(id, item); ci++; continue; } // already native
                switch (convertItem(id, item, res, label, notes, items, blocks, furniture)) {
                    case "blocks" -> cb++;
                    case "furniture" -> cf++;
                    case "items" -> ci++;
                    default -> { }
                }
            }
        }

        Map<String, Object> fsrc = map(doc.get("font_images"));
        if (fsrc != null) {
            for (Map.Entry<String, Object> e : fsrc.entrySet()) {
                Map<String, Object> f = map(e.getValue());
                if (f == null) { notes.add(label + ": font image '" + e.getKey() + "' is not a section, skipped"); continue; }
                if (!f.containsKey("path")) { fonts.put(e.getKey(), f); cfont++; continue; } // already native
                String path = str(f.get("path"), null);
                if (path == null || path.contains(":")) {
                    notes.add(label + ": font image '" + e.getKey() + "' uses path '" + path + "' (internal/namespaced), skipped");
                    continue;
                }
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("texture", png(path));
                if (f.get("scale_ratio") != null) n.put("height", (int) num(f.get("scale_ratio"), 9));
                if (f.get("y_position") != null) n.put("ascent", (int) num(f.get("y_position"), 8));
                for (String k : f.keySet()) {
                    if (!Set.of("path", "scale_ratio", "y_position").contains(k)) {
                        notes.add(label + ": font image '" + e.getKey() + "': ignored key '" + k + "'");
                    }
                }
                fonts.put(e.getKey(), n);
                cfont++;
            }
        }

        // other top-level sections
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : doc.keySet()) {
            if (k.equals("info") || k.equals("items") || k.equals("font_images")) continue;
            if (NATIVE_PASSTHROUGH.contains(k) && map(doc.get(k)) != null) {
                Map<String, Object> target = switch (k) {
                    case "blocks" -> blocks;
                    case "furniture" -> furniture;
                    default -> null;
                };
                if (target != null) target.putAll(map(doc.get(k)));
                else out.put(k, doc.get(k));
                continue;
            }
            notes.add(label + ": section '" + k + "' is not supported yet, ignored");
        }
        if (!items.isEmpty()) out.put("items", items);
        if (!blocks.isEmpty()) out.put("blocks", blocks);
        if (!furniture.isEmpty()) out.put("furniture", furniture);
        if (!fonts.isEmpty()) out.put("font_images", fonts);
        return new Result(out, namespaceOf(doc), notes, ci, cb, cf, cfont);
    }

    /** returns the native section the item went to ("items", "blocks", "furniture") or "" when skipped */
    private static String convertItem(String id, Map<String, Object> item, Map<String, Object> res, String label,
                                      List<String> notes, Map<String, Object> items, Map<String, Object> blocks,
                                      Map<String, Object> furniture) {
        String who = label + ": item '" + id + "'";

        // ---- model source
        List<Object> textures = list(res.get("textures"));
        String modelPath = str(res.get("model_path"), null);
        boolean generate = res.get("generate") == null ? modelPath == null : bool(res.get("generate"), true);
        String texture = null, model = null;
        String firstTex = textures.isEmpty() ? null : str(textures.get(0), null);
        if (generate) {
            texture = firstTex == null ? null : png(firstTex);
        } else if (modelPath != null) {
            model = modelPath.endsWith(".json") ? modelPath.substring(0, modelPath.length() - 5) : modelPath;
        } else if (firstTex != null) {
            texture = png(firstTex);
            notes.add(who + ": generate=false without model_path, using the first texture");
        }
        if (texture == null && model == null) {
            notes.add(who + ": no textures and no model_path, skipped");
            return "";
        }
        if (generate && textures.size() > 1) notes.add(who + ": " + textures.size() + " textures, only the first is used");

        // ---- common fields
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("display_name", Legacy.toMiniMessage(str(item.get("display_name"), id)));
        List<Object> lore = list(item.get("lore"));
        if (!lore.isEmpty()) {
            List<Object> l = new ArrayList<>();
            for (Object o : lore) l.add(Legacy.toMiniMessage(String.valueOf(o)));
            n.put("lore", l);
        }
        n.put("base", str(res.get("material"), "PAPER").toUpperCase(Locale.ROOT));
        if (texture != null) n.put("texture", texture); else n.put("model", model);

        for (String k : res.keySet()) {
            if (!Set.of("material", "generate", "textures", "model_path").contains(k)) {
                notes.add(who + ": resource." + k + " ignored" + (k.equals("model_id") ? " (ids are assigned automatically)" : ""));
            }
        }
        for (String k : item.keySet()) {
            if (!Set.of("display_name", "lore", "resource", "behaviours", "specific_properties").contains(k)) {
                notes.add(who + ": ignored key '" + k + "'");
            }
        }

        // ---- kind
        Map<String, Object> beh = map(item.get("behaviours"));
        Map<String, Object> sp = map(item.get("specific_properties"));
        Object furn = beh == null ? null : beh.get("furniture");
        Map<String, Object> block = sp == null ? null : map(sp.get("block"));

        if (beh != null) {
            Object hat = beh.get("hat");
            if (hat != null && hat != Boolean.FALSE) n.put("hat", true);
            for (String k : beh.keySet()) {
                if (!k.equals("furniture") && !k.equals("hat")) notes.add(who + ": behaviour '" + k + "' ignored");
            }
        }
        if (sp != null) for (String k : sp.keySet()) if (!k.equals("block")) notes.add(who + ": specific_properties." + k + " ignored");

        if (furn != null && furn != Boolean.FALSE) {
            Map<String, Object> fm = map(furn);
            Map<String, Object> hb = fm == null ? null : map(fm.get("hitbox"));
            if (hb != null) {
                double w = num(hb.get("width"), 1), l = num(hb.get("length"), w), h = num(hb.get("height"), 1);
                Map<String, Object> hitbox = new LinkedHashMap<>();
                hitbox.put("width", Math.max(w, l));
                hitbox.put("height", h);
                n.put("hitbox", hitbox);
            }
            if (fm != null) for (String k : fm.keySet()) if (!k.equals("hitbox")) notes.add(who + ": furniture." + k + " ignored");
            furniture.put(id, n);
            return "furniture";
        }
        if (block != null) {
            Map<String, Object> pm = map(block.get("placed_model"));
            String type = pm == null ? null : str(pm.get("type"), null);
            if (type == null || !type.equalsIgnoreCase("REAL_NOTE")) {
                notes.add(who + ": placed_model type '" + type + "' imported as a note block instead");
            }
            for (String k : block.keySet()) if (!k.equals("placed_model")) notes.add(who + ": block." + k + " ignored");
            n.remove("base"); // block items are always note blocks
            blocks.put(id, n);
            return "blocks";
        }
        items.put(id, n);
        return "items";
    }

    // ------------------------------------------------------------ helpers

    private static String png(String rel) {
        return rel.toLowerCase(Locale.ROOT).endsWith(".png") ? rel : rel + ".png";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        if (o instanceof List<?> l) return (List<Object>) l;
        if (o instanceof String s) return List.of(s);
        return List.of();
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; } }
        return def;
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return s.equalsIgnoreCase("true");
        return def;
    }
}
