import dev.veyrion.item.imports.*;
import dev.veyrion.item.pack.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Run: java -cp <classes> e2e/ImportE2E.java   (needs /tmp/vi-import/*.json from e2e/yaml2json.py and the real sample in /tmp/vi-real) */
public class ImportE2E {
    interface T { void run() throws Exception; }
    static int pass, fail;
    static void run(String id, String what, T t) {
        try { t.run(); pass++; System.out.printf("PASS %-7s %s%n", id, what); }
        catch (Throwable e) { fail++; System.out.printf("FAIL %-7s %s  ->  %s%n", id, what, e); }
    }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    @SuppressWarnings("unchecked") static Map<String, Object> m(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static Map<String, Object> load(String f) throws Exception {
        return (Map<String, Object>) Json.parse(Files.readString(Path.of(f)));
    }
    static boolean noteHas(ItemsAdderImporter.Result r, String... parts) {
        return r.notes().stream().anyMatch(n -> Arrays.stream(parts).allMatch(n::contains));
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> ia = load("/tmp/vi-import/ia_sample.json");
        var res = ItemsAdderImporter.convert(ia, "demo_ia/sample.yml");
        Map<String, Object> out = res.nativeDoc();

        run("I-H1", "ItemsAdder file detected, namespace read from info.namespace", () -> {
            check(ItemsAdderImporter.looksLikeItemsAdder(ia), "not detected");
            check("demo_ia".equals(res.namespace()), "ns " + res.namespace());
        });
        run("I-H2", "counts: 3 items, 2 blocks, 1 furniture, 1 font image (broken/internal skipped)", () -> {
            check(res.items() == 3 && res.blocks() == 2 && res.furniture() == 1 && res.fonts() == 1,
                    res.items() + "/" + res.blocks() + "/" + res.furniture() + "/" + res.fonts());
        });
        run("I-H3", "legacy colors -> MiniMessage (&c&l, &#hex, &7 in lore)", () -> {
            Map<String, Object> ruby = m(m(out.get("items")).get("ruby"));
            String name = (String) ruby.get("display_name");
            check(name.contains("<red>") && name.contains("<bold>Ruby ") && name.contains("<#ff8800>gold"), name);
            check(((List<?>) ruby.get("lore")).get(0).toString().contains("<gray>A shiny gem"), "lore");
        });
        run("I-H4", "texture without .png gets it; material kept", () -> {
            Map<String, Object> sw = m(m(out.get("items")).get("ruby_sword"));
            check("item/sword.png".equals(sw.get("texture")) && "DIAMOND_SWORD".equals(sw.get("base")), sw.toString());
        });
        run("I-H5", "REAL_NOTE block -> blocks section, texture kept, no base", () -> {
            Map<String, Object> b = m(m(out.get("blocks")).get("ruby_block"));
            check("block/ruby_block.png".equals(b.get("texture")) && !b.containsKey("base"), b.toString());
        });
        run("I-H6", "furniture: model path + hitbox (max of length/width, height)", () -> {
            Map<String, Object> f = m(m(out.get("furniture")).get("chair"));
            Map<String, Object> hb = m(f.get("hitbox"));
            check("furniture/chair".equals(f.get("model")), f.toString());
            check(((Number) hb.get("width")).doubleValue() == 1.5 && ((Number) hb.get("height")).doubleValue() == 2.0, hb.toString());
        });
        run("I-H7", "font image: path/scale_ratio/y_position -> texture/height/ascent", () -> {
            Map<String, Object> f = m(m(out.get("font_images")).get("heart"));
            check("font/heart.png".equals(f.get("texture")) && ((Number) f.get("height")).intValue() == 9
                    && ((Number) f.get("ascent")).intValue() == 8, f.toString());
        });
        run("I-H8", "hat behaviour -> hat: true", () -> {
            check(Boolean.TRUE.equals(m(m(out.get("items")).get("crown")).get("hat")), "no hat flag");
        });
        run("I-E1", "everything not understood is reported, nothing silent", () -> {
            check(noteHas(res, "ruby_sword", "durability"), "durability");
            check(noteHas(res, "ruby_sword", "armor"), "armor");
            check(noteHas(res, "ruby_block", "hardness"), "hardness");
            check(noteHas(res, "ruby_wire", "REAL_WIRE"), "REAL_WIRE");
            check(noteHas(res, "ruby_wire", "only the first"), "multi texture");
            check(noteHas(res, "chair", "solid"), "furniture.solid");
            check(noteHas(res, "broken", "skipped"), "broken");
            check(noteHas(res, "internal", "skipped"), "internal font");
            check(noteHas(res, "recipes"), "recipes section");
        });
        run("I-E2", "native-format doc is not treated as ItemsAdder", () -> {
            Map<String, Object> nat = m(Json.parse("{\"items\":{\"a\":{\"texture\":\"item/a.png\",\"base\":\"PAPER\"}}}"));
            check(!ItemsAdderImporter.looksLikeItemsAdder(nat), "false positive");
        });
        run("I-E3", "garbage input does not crash (null/str items, numeric namespace)", () -> {
            var r = ItemsAdderImporter.convert(m(Json.parse("{\"info\":{\"namespace\":5},\"items\":{\"a\":null,\"b\":\"str\"},\"font_images\":{\"c\":3}}")), "x");
            check(r.items() == 0 && r.notes().size() >= 3, r.notes().toString());
        });
        run("I-E4", "mixed file: IA item + native item both end up in items", () -> {
            var r = ItemsAdderImporter.convert(m(Json.parse(
                    "{\"info\":{\"namespace\":\"x\"},\"items\":{\"ia\":{\"resource\":{\"textures\":[\"item/a\"]}},\"nat\":{\"texture\":\"item/b.png\"}}}")), "x");
            check(m(r.nativeDoc().get("items")).keySet().equals(Set.of("ia", "nat")), r.nativeDoc().toString());
        });
        run("I-E5", "Legacy converter: §, &r, uppercase, bad hex, trailing &", () -> {
            check(Legacy.toMiniMessage("\u00a7aGreen").equals("<reset><green>Green"), Legacy.toMiniMessage("\u00a7aGreen"));
            check(Legacy.toMiniMessage("&Ared&rx").equals("<reset><green>red<reset>x"), Legacy.toMiniMessage("&Ared&rx"));
            check(Legacy.toMiniMessage("&#12345 x &").equals("&#12345 x &"), Legacy.toMiniMessage("&#12345 x &"));
            check(Legacy.toMiniMessage(null).isEmpty(), "null");
        });

        // ---------------- the real uploaded sample: naed_cosmetics ----------------
        Path nsDir = Path.of("/tmp/vi-real/naed_cosmetics");
        Map<String, Path> files = ResourceFolders.collect(nsDir);
        Map<String, Object> naed = load("/tmp/vi-import/naed.json");
        var rr = ItemsAdderImporter.convert(naed, "naed_cosmetics/cosmetics.yml");

        run("R-H1", "real sample: resourcepack/<ns>/... is mapped to assets/<ns>/...", () -> {
            check(files.containsKey("assets/naed_cosmetics/models/item/eye_scar.json"), files.keySet().toString());
            check(files.containsKey("assets/naed_cosmetics/textures/item/eye_scar_texture.png"), files.keySet().toString());
            check(files.size() == 2, "files " + files.size());
        });
        run("R-H2", "real sample: namespace, model_path item, hat, name converted", () -> {
            check("naed_cosmetics".equals(rr.namespace()) && rr.items() == 1, rr.namespace() + " " + rr.items());
            Map<String, Object> it = m(m(rr.nativeDoc().get("items")).get("eye_scar"));
            check("item/eye_scar".equals(it.get("model")) && "PAPER".equals(it.get("base")), it.toString());
            check(Boolean.TRUE.equals(it.get("hat")), "hat");
            check("<reset><red>Dragon Eye Scar".equals(it.get("display_name")), (String) it.get("display_name"));
        });
        run("R-E1", "real sample: unsupported parts are reported (permission, categories)", () -> {
            check(noteHas(rr, "eye_scar", "permission"), rr.notes().toString());
            check(noteHas(rr, "categories"), rr.notes().toString());
        });
        run("R-H3", "real sample -> packs for 1.21.1..1.21.11 with the model, texture and custom_model_data wiring", () -> {
            Map<String, Object> it = m(m(rr.nativeDoc().get("items")).get("eye_scar"));
            String ns = rr.namespace(), model = (String) it.get("model");
            check(files.containsKey("assets/" + ns + "/models/" + model + ".json"), "model missing for loader check");
            var c = new PackContent("Real sample", files,
                    List.of(new PackContent.ModelEntry(ns, "eye_scar", "paper", 1000, null, ns + ":" + model)), List.of(), List.of());
            Path outDir = Path.of("/tmp/vi-real-out/main");
            var results = PackGenerator.generate(c, McVersion.range("1.21.1", "1.21.11"), outDir);
            Files.writeString(outDir.getParent().resolve("expected_description.txt"), "Real sample");
            check(results.size() == 11, "11 zips");
            for (var r : results) {
                try (ZipFile z = new ZipFile(r.file().toFile())) {
                    check(z.getEntry("assets/naed_cosmetics/models/item/eye_scar.json") != null, "model in zip");
                    check(z.getEntry("assets/naed_cosmetics/textures/item/eye_scar_texture.png") != null, "texture in zip");
                    boolean modern = r.version().modernItemModels();
                    String base = new String(z.getInputStream(z.getEntry(modern
                            ? "assets/minecraft/items/paper.json" : "assets/minecraft/models/item/paper.json")).readAllBytes());
                    check(base.contains("naed_cosmetics:item/eye_scar") && base.contains("1000"), r.version().name() + " wiring");
                }
            }
        });

        System.out.printf("%n%d passed, %d failed%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
