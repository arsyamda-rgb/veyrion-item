import dev.veyrion.item.pack.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Run: java -cp <classes> e2e/PackE2E.java   (no dependencies). Writes packs to /tmp/vi-e2e for validate_packs.py */
public class PackE2E {
    interface T { void run() throws Exception; }
    static int pass, fail;
    static void run(String id, String what, T t) {
        try { t.run(); pass++; System.out.printf("PASS %-8s %s%n", id, what); }
        catch (Throwable e) { fail++; System.out.printf("FAIL %-8s %s  ->  %s%n", id, what, e.getMessage()); }
    }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    static final Path RP = Path.of("src/main/resources/contents/demo/resource_pack");
    static final Path OUT = Path.of("/tmp/vi-e2e");

    static Map<String, Path> demoFiles() throws IOException {
        Map<String, Path> files = new TreeMap<>();
        try (Stream<Path> w = Files.walk(RP)) {
            w.filter(Files::isRegularFile).forEach(f -> files.put(RP.relativize(f).toString().replace('\\', '/'), f));
        }
        return files;
    }
    static Path tmpFile(String content) throws IOException {
        Path p = Files.createTempFile("vi", ".json");
        Files.writeString(p, content);
        return p;
    }
    static PackContent demo(String desc, Map<String, Path> files) {
        return new PackContent(desc, files,
                List.of(new PackContent.ModelEntry("demo", "ruby", "paper", 1000, "demo:item/ruby", null),
                        new PackContent.ModelEntry("demo", "armchair", "paper", 1001, "demo:item/armchair", null),
                        new PackContent.ModelEntry("demo", "ruby_block", "note_block", 1002, null, "demo:generated/block_ruby_block")),
                List.of(new PackContent.BlockEntry("demo", "ruby_block", 0, "demo:block/ruby_block", null)),
                List.of(new PackContent.FontImageEntry("demo", "heart", 0xE000, "demo:font/heart.png", 8, 9)));
    }
    static String entry(Path zip, String name) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            var e = z.getEntry(name);
            if (e == null) return null;
            return new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
    static List<PackGenerator.Result> gen(PackContent c, String sub) throws IOException {
        return PackGenerator.generate(c, McVersion.range("1.21.1", "1.21.11"), OUT.resolve(sub));
    }

    public static void main(String[] args) throws Exception {
        try (Stream<Path> w = Files.exists(OUT) ? Files.walk(OUT) : Stream.empty()) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        String DESC = "Pūrple ✨ 日本語 \"quoted\"";
        List<PackGenerator.Result> main = gen(demo(DESC, demoFiles()), "main");
        Files.writeString(OUT.resolve("expected_description.txt"), DESC);

        // ---------------- F01 generate packs ----------------
        run("F01-H1", "11 zips + manifest row per version 1.21.1..1.21.11", () -> {
            check(main.size() == 11, "got " + main.size());
            String man = Files.readString(OUT.resolve("main/manifest.txt"));
            for (McVersion v : McVersion.ALL) {
                check(man.contains(v.name()), "manifest misses " + v.name());
                check(Files.size(OUT.resolve("main/pack-" + v.name() + ".zip")) > 0, "empty zip " + v.name());
            }
        });
        run("F01-H2", "legacy models <=1.21.3, items/ definitions >=1.21.4, never both", () -> {
            for (var r : main) {
                boolean modern = r.version().packFormat() >= 46;
                boolean hasItems = entry(r.file(), "assets/minecraft/items/paper.json") != null;
                boolean hasLegacy = entry(r.file(), "assets/minecraft/models/item/paper.json") != null;
                check(hasItems == modern && hasLegacy == !modern, r.version().name() + " items=" + hasItems + " legacy=" + hasLegacy);
            }
        });
        run("F01-H3", "pack.mcmeta: right pack_format, min/max_format only from 1.21.9", () -> {
            for (var r : main) {
                String m = entry(r.file(), "pack.mcmeta");
                int f = r.version().packFormat();
                check(m.contains("\"pack_format\": " + f), r.version().name() + " pack_format");
                check(m.contains("min_format") == (f >= 69), r.version().name() + " min_format presence");
            }
        });
        run("F01-E1", "same input twice -> byte-identical zips (stable SHA-1)", () -> {
            var again = gen(demo(DESC, demoFiles()), "again");
            for (int i = 0; i < main.size(); i++) {
                check(main.get(i).sha1().equals(again.get(i).sha1()), "sha differs for " + main.get(i).version().name());
            }
        });
        run("F01-E2", "versions with the same pack_format share identical bytes", () -> {
            Map<Integer, String> seen = new HashMap<>();
            for (var r : main) {
                String prev = seen.put(r.version().packFormat(), r.sha1());
                check(prev == null || prev.equals(r.sha1()), "format " + r.version().packFormat() + " differs");
            }
            check(seen.size() == 8, "expected 8 distinct formats, got " + seen.size());
        });
        run("F01-E3", "empty content still yields a valid pack", () -> {
            var r = gen(new PackContent("empty", Map.of(), List.of(), List.of(), List.of()), "empty");
            check(r.size() == 11, "11 zips");
            check(entry(r.get(0).file(), "pack.mcmeta") != null, "mcmeta");
            check(entry(r.get(0).file(), "assets/minecraft/font/default.json") != null, "font");
            check(entry(r.get(10).file(), "assets/minecraft/items/paper.json") == null, "no items file");
        });
        run("F01-E4", "emoji/accents/CJK/quotes in description survive (validated in python)", () -> {
            String m = entry(main.get(0).file(), "pack.mcmeta");
            check(m.contains("\\u0075") || m.contains("\\u016b"), "expected \\u-escaped accent: " + m);
        });
        run("F01-E5", "overrides/thresholds sorted by cmd even if input is shuffled", () -> {
            var c = new PackContent("x", demoFiles(),
                    List.of(new PackContent.ModelEntry("demo", "c", "paper", 1005, "demo:item/ruby", null),
                            new PackContent.ModelEntry("demo", "a", "paper", 1001, "demo:item/ruby", null),
                            new PackContent.ModelEntry("demo", "b", "paper", 1003, "demo:item/ruby", null)),
                    List.of(), List.of());
            var r = gen(c, "order");
            String legacy = entry(r.get(0).file(), "assets/minecraft/models/item/paper.json");
            String modern = entry(r.get(10).file(), "assets/minecraft/items/paper.json");
            for (String s : List.of(legacy, modern)) {
                check(s.indexOf("1001") < s.indexOf("1003") && s.indexOf("1003") < s.indexOf("1005"), "not sorted");
            }
        });
        run("F01-E6", "a pack.mcmeta inside resource_pack/ does not override the generated one", () -> {
            Map<String, Path> f = demoFiles();
            f.put("pack.mcmeta", tmpFile("{\"pack\":{\"pack_format\":1,\"description\":\"evil\"}}"));
            var r = gen(demo("ok", f), "mcmeta");
            check(entry(r.get(0).file(), "pack.mcmeta").contains("\"pack_format\": 34"), "user mcmeta won");
        });
        run("F01-E7", "user's own assets/minecraft/font/default.json providers are kept", () -> {
            Map<String, Path> f = demoFiles();
            f.put("assets/minecraft/font/default.json", tmpFile(
                    "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:font/mine.png\",\"ascent\":8,\"height\":9,\"chars\":[\"\\ue100\"]}]}"));
            var r = gen(demo("ok", f), "userfont");
            check(entry(r.get(0).file(), "assets/minecraft/font/default.json").contains("demo:font/mine.png"),
                    "user provider was silently replaced by the generated font file");
        });
        run("F01-E8", "user font providers are merged with generated ones (both present, JSON valid)", () -> {
            Map<String, Path> f = demoFiles();
            f.put("assets/minecraft/font/default.json", tmpFile(
                    "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:font/mine.png\",\"ascent\":8,\"height\":9,\"chars\":[\"\\ue100\"]}]}"));
            var r = gen(demo("ok", f), "userfont2");
            String s = entry(r.get(0).file(), "assets/minecraft/font/default.json");
            check(s.contains("demo:font/mine.png") && s.contains("demo:font/heart.png") && s.contains("\"type\": \"space\""), "missing a provider");
            check(Json.parse(s) instanceof Map, "not valid JSON");
        });
        run("F01-N4", "malformed user default.json -> clear IOException, not a silently wrong pack", () -> {
            Map<String, Path> f = demoFiles();
            f.put("assets/minecraft/font/default.json", tmpFile("{\"providers\": [ oops"));
            try { gen(demo("x", f), "badfont"); throw new AssertionError("no exception"); }
            catch (IOException e) { check(e.getMessage().contains("default.json"), e.getMessage()); }
        });
        run("F01-N5", "Json.parse round trip incl. escapes, surrogate pairs, numbers, nesting", () -> {
            String src = "{\"a\":[1,2.5,-3e2,true,null,\"x\\n\\u00e9\\ud83d\\ude00\"],\"b\":{}}";
            Object v = Json.parse(src);
            check(Json.parse(Json.write(v)).equals(v), "round trip differs");
            try { Json.parse("{\"a\":"); throw new AssertionError("truncated accepted"); } catch (IllegalArgumentException e) { }
            try { Json.parse("[1,2] x"); throw new AssertionError("trailing accepted"); } catch (IllegalArgumentException e) { }
        });
        run("F01-N1", "bad version input: reversed range, unknown, whitespace", () -> {
            try { McVersion.range("1.21.11", "1.21.1"); throw new AssertionError("reversed range accepted"); }
            catch (IllegalArgumentException expected) { }
            try { McVersion.parse("1.20.4"); throw new AssertionError("unknown accepted"); }
            catch (IllegalArgumentException expected) { }
            check(McVersion.parse(" 1.21.4 ").packFormat() == 46, "trim");
        });
        run("F01-N2", "missing source file fails loudly (IOException), no partial silent pack", () -> {
            Map<String, Path> f = new TreeMap<>(); f.put("assets/demo/textures/item/ghost.png", Path.of("/nope/ghost.png"));
            try { gen(demo("x", f), "missing"); throw new AssertionError("no exception"); }
            catch (IOException expected) { }
        });
        run("F01-N3", "item with neither texture nor model -> error names the item", () -> {
            var c = new PackContent("x", Map.of(), List.of(new PackContent.ModelEntry("demo", "broken", "paper", 1, null, null)), List.of(), List.of());
            try { gen(c, "broken"); throw new AssertionError("no exception"); }
            catch (NullPointerException e) { check(e.getMessage().contains("broken"), e.getMessage()); }
        });

        // ---------------- F03 custom blocks ----------------
        run("F03-H1", "note_block.json: 23 instruments x 25 notes x 2 = 1150 variants, custom state mapped for powered true+false", () -> {
            String s = entry(main.get(0).file(), "assets/minecraft/blockstates/note_block.json");
            check(count(s, "\"model\":") == 1150, "variants " + count(s, "\"model\":"));
            check(count(s, "demo:generated/block_ruby_block") == 2, "custom mapped " + count(s, "demo:generated/block_ruby_block"));
        });
        run("F03-E1", "state pool: 550 unique (instrument,note), harp never used", () -> {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < BlockStates.capacity(); i++) {
                check(!BlockStates.instrument(i).equals("harp"), "harp at " + i);
                check(seen.add(BlockStates.instrument(i) + "," + BlockStates.note(i)), "duplicate at " + i);
            }
            check(seen.size() == 550, "size " + seen.size());
        });
        run("F03-E2", "last state (549) builds; state 550 throws (loader must guard capacity)", () -> {
            var ok = new PackContent("x", demoFiles(), List.of(), List.of(new PackContent.BlockEntry("demo", "b", 549, "demo:block/ruby_block", null)), List.of());
            gen(ok, "cap");
            var bad = new PackContent("x", demoFiles(), List.of(), List.of(new PackContent.BlockEntry("demo", "b", 550, "demo:block/ruby_block", null)), List.of());
            try { gen(bad, "cap2"); throw new AssertionError("state 550 accepted"); }
            catch (IndexOutOfBoundsException expected) { }
        });
        run("F03-E3", "block with own model uses it and generates no cube model", () -> {
            var c = new PackContent("x", demoFiles(), List.of(),
                    List.of(new PackContent.BlockEntry("demo", "custom", 3, null, "demo:block/custom")), List.of());
            var r = gen(c, "blockmodel");
            check(entry(r.get(0).file(), "assets/demo/models/generated/block_custom.json") == null, "generated anyway");
            check(entry(r.get(0).file(), "assets/minecraft/blockstates/note_block.json").contains("demo:block/custom"), "not mapped");
        });

        // ---------------- F05 font images / spaces ----------------
        run("F05-H1", "Spaces.of(n) moves the cursor exactly n px for n in -3000..3000", () -> {
            Map<String, Object> adv = Spaces.advances();
            for (int n = -3000; n <= 3000; n++) {
                int sum = 0;
                for (char ch : Spaces.of(n).toCharArray()) sum += (Integer) adv.get(String.valueOf(ch));
                check(sum == n, "n=" + n + " sum=" + sum);
            }
        });
        run("F05-H2", "font provider: one code point per glyph, space provider present", () -> {
            String f = entry(main.get(0).file(), "assets/minecraft/font/default.json");
            check(f.contains("\"type\": \"space\"") && f.contains("\\ue000"), "providers");
        });

        System.out.printf("%n%d passed, %d failed%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
