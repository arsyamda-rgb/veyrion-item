package dev.veyrion.item.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Builds the file tree of one resource pack for one Minecraft version.
 *
 * The whole point of the multi-version support lives here:
 *  - pack.mcmeta differs per version (pack_format, and min/max_format from 1.21.9)
 *  - <= 1.21.3: models/item/<base>.json with "overrides" on custom_model_data
 *  - >= 1.21.4: items/<base>.json with a range_dispatch on custom_model_data
 */
public final class PackBuilder {
    private PackBuilder() {}

    private static final Set<String> BLOCK_BASES = Set.of("note_block");
    private static final List<String> HANDHELD_SUFFIXES =
            List.of("_sword", "_axe", "_pickaxe", "_shovel", "_hoe");

    public static Map<String, byte[]> build(PackContent c, McVersion v) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        // everything from contents/<ns>/resource_pack/ goes in first; generated files below win on conflict
        for (Map.Entry<String, Path> f : c.files().entrySet()) {
            if (f.getKey().equals("pack.mcmeta")) continue;
            out.put(f.getKey(), Files.readAllBytes(f.getValue()));
        }
        out.put("pack.mcmeta", utf8(Json.write(mcmeta(c.description(), v))));

        // ---- item models -------------------------------------------------
        Map<String, List<PackContent.ModelEntry>> byBase = new TreeMap<>();
        for (PackContent.ModelEntry m : c.models()) {
            byBase.computeIfAbsent(m.base(), k -> new ArrayList<>()).add(m);
            if (m.modelRef() != null) continue; // your own model (or a block item): nothing to generate
            Objects.requireNonNull(m.textureRef(), "item " + m.id() + " needs a texture or model");
            out.put("assets/" + m.ns() + "/models/generated/item_" + m.id() + ".json", utf8(Json.write(map(
                    "parent", isHandheld(m.base()) ? "minecraft:item/handheld" : "minecraft:item/generated",
                    "textures", map("layer0", m.textureRef())))));
        }

        for (Map.Entry<String, List<PackContent.ModelEntry>> e : byBase.entrySet()) {
            String base = e.getKey();
            List<PackContent.ModelEntry> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparingInt(PackContent.ModelEntry::cmd));
            if (v.modernItemModels()) {
                out.put("assets/minecraft/items/" + base + ".json", utf8(Json.write(modernBase(base, list))));
            } else {
                out.put("assets/minecraft/models/item/" + base + ".json", utf8(Json.write(legacyBase(base, list))));
            }
        }

        // ---- custom blocks (note block states) ---------------------------
        if (!c.blocks().isEmpty()) {
            Map<String, String> custom = new HashMap<>();
            for (PackContent.BlockEntry b : c.blocks()) {
                String model = b.modelRef();
                if (model == null) {
                    Objects.requireNonNull(b.textureRef(), "block " + b.id() + " needs a texture or model");
                    out.put("assets/" + b.ns() + "/models/generated/block_" + b.id() + ".json", utf8(Json.write(map(
                            "parent", "minecraft:block/cube_all",
                            "textures", map("all", b.textureRef())))));
                    model = b.ns() + ":generated/block_" + b.id();
                }
                custom.put(BlockStates.instrument(b.state()) + "," + BlockStates.note(b.state()), model);
            }
            Map<String, Object> variants = new LinkedHashMap<>();
            for (String inst : BlockStates.INSTRUMENTS) {
                for (int note = 0; note < 25; note++) {
                    String model = custom.getOrDefault(inst + "," + note, "minecraft:block/note_block");
                    for (String powered : List.of("false", "true")) {
                        variants.put("instrument=" + inst + ",note=" + note + ",powered=" + powered,
                                map("model", model));
                    }
                }
            }
            out.put("assets/minecraft/blockstates/note_block.json",
                    utf8(Json.write(map("variants", variants))));
        }

        // ---- fonts: images + space glyphs --------------------------------
        List<Object> providers = new ArrayList<>();
        byte[] userFont = out.get("assets/minecraft/font/default.json"); // from resource_pack/: keep its providers
        if (userFont != null) {
            try {
                Object parsed = Json.parse(new String(userFont, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> m) || !(m.get("providers") instanceof List<?> l)) {
                    throw new IllegalArgumentException("no \"providers\" array");
                }
                providers.addAll(l);
            } catch (IllegalArgumentException e) {
                throw new IOException("resource_pack assets/minecraft/font/default.json is not a valid font file: " + e.getMessage(), e);
            }
        }
        for (PackContent.FontImageEntry f : c.fonts()) {
            providers.add(map(
                    "type", "bitmap",
                    "file", f.fileRef(),
                    "ascent", f.ascent(),
                    "height", f.height(),
                    "chars", List.of(new String(Character.toChars(f.codepoint())))));
        }
        providers.add(map("type", "space", "advances", Spaces.advances()));
        out.put("assets/minecraft/font/default.json", utf8(Json.write(map("providers", providers))));
        return out;
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> mcmeta(String description, McVersion v) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("pack_format", v.packFormat());
        if (v.minMaxFormat()) {
            pack.put("min_format", v.packFormat());
            pack.put("max_format", v.packFormat());
        }
        pack.put("description", description);
        return map("pack", pack);
    }

    /** 1.21.4+: assets/minecraft/items/<base>.json */
    private static Map<String, Object> modernBase(String base, List<PackContent.ModelEntry> list) {
        List<Object> entries = new ArrayList<>();
        for (PackContent.ModelEntry m : list) {
            entries.add(map("threshold", m.cmd(), "model", map("type", "minecraft:model", "model", modelId(m))));
        }
        return map("model", map(
                "type", "minecraft:range_dispatch",
                "property", "minecraft:custom_model_data",
                "index", 0,
                "fallback", map("type", "minecraft:model", "model", "minecraft:item/" + base),
                "entries", entries));
    }

    /** 1.21.1 - 1.21.3: models/item/<base>.json with overrides */
    private static Map<String, Object> legacyBase(String base, List<PackContent.ModelEntry> list) {
        List<Object> overrides = new ArrayList<>();
        for (PackContent.ModelEntry m : list) {
            overrides.add(map("predicate", map("custom_model_data", m.cmd()), "model", modelId(m)));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        if (BLOCK_BASES.contains(base)) {
            root.put("parent", "minecraft:block/" + base);
        } else {
            root.put("parent", isHandheld(base) ? "minecraft:item/handheld" : "minecraft:item/generated");
            root.put("textures", map("layer0", "minecraft:item/" + base));
        }
        root.put("overrides", overrides);
        return root;
    }

    private static String modelId(PackContent.ModelEntry m) {
        return m.modelRef() != null ? m.modelRef() : m.ns() + ":generated/item_" + m.id();
    }

    private static boolean isHandheld(String base) {
        for (String s : HANDHELD_SUFFIXES) if (base.endsWith(s)) return true;
        return false;
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

}
