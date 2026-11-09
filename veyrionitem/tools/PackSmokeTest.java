import dev.veyrion.item.pack.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class PackSmokeTest {
    public static void main(String[] a) throws Exception {
        // same layout the plugin reads: contents/<ns>/resource_pack/
        Path rp = Path.of("src/main/resources/contents/demo/resource_pack");
        Map<String, Path> files = new TreeMap<>();
        try (Stream<Path> w = Files.walk(rp)) {
            w.filter(Files::isRegularFile).forEach(f -> files.put(rp.relativize(f).toString().replace('\\', '/'), f));
        }
        // a user-made model + an extra asset that must be copied through untouched
        Path tmp = Files.createTempDirectory("vi");
        Files.writeString(tmp.resolve("chair.json"), "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:item/armchair\"}}");
        files.put("assets/demo/models/item/chair.json", tmp.resolve("chair.json"));
        Files.writeString(tmp.resolve("sounds.json"), "{}");
        files.put("assets/demo/sounds.json", tmp.resolve("sounds.json"));

        PackContent c = new PackContent("VeyrionItem test", files,
                List.of(new PackContent.ModelEntry("demo", "ruby", "paper", 1000, "demo:item/ruby", null),
                        new PackContent.ModelEntry("demo", "chair", "paper", 1001, null, "demo:item/chair"),
                        new PackContent.ModelEntry("demo", "ruby_block", "note_block", 1002, null, "demo:generated/block_ruby_block")),
                List.of(new PackContent.BlockEntry("demo", "ruby_block", 0, "demo:block/ruby_block", null)),
                List.of(new PackContent.FontImageEntry("demo", "heart", 0xE000, "demo:font/heart.png", 8, 9)));

        Path out = Path.of("/tmp/vi-out");
        List<PackGenerator.Result> r = PackGenerator.generate(c, McVersion.range("1.21.1", "1.21.11"), out);
        check(r.size() == 11, "11 versions");
        for (PackGenerator.Result x : r) {
            try (ZipFile z = new ZipFile(x.file().toFile())) {
                boolean modern = x.version().modernItemModels();
                String n = x.version().name();
                check((z.getEntry("assets/minecraft/items/paper.json") != null) == modern, n + " items/ presence");
                check((z.getEntry("assets/minecraft/models/item/paper.json") != null) == !modern, n + " legacy models presence");
                check(z.getEntry("assets/minecraft/blockstates/note_block.json") != null, n + " blockstate");
                check(z.getEntry("assets/minecraft/font/default.json") != null, n + " font");
                check(z.getEntry("assets/demo/textures/item/ruby.png") != null, n + " texture copied from resource_pack");
                check(z.getEntry("assets/demo/sounds.json") != null, n + " extra asset copied");
                check(z.getEntry("assets/demo/models/item/chair.json") != null, n + " user model copied");
                check(z.getEntry("assets/demo/models/generated/item_ruby.json") != null, n + " generated item model");
                check(z.getEntry("assets/demo/models/generated/block_ruby_block.json") != null, n + " generated block model");
            }
        }
        System.out.println(Files.readString(out.resolve("manifest.txt")));
        try (ZipFile z = new ZipFile(out.resolve("pack-1.21.4.zip").toFile())) {
            System.out.println("--- 1.21.4 items/paper.json ---");
            System.out.println(new String(z.getInputStream(z.getEntry("assets/minecraft/items/paper.json")).readAllBytes()));
            System.out.println("--- font/default.json (first 400 chars) ---");
            String f = new String(z.getInputStream(z.getEntry("assets/minecraft/font/default.json")).readAllBytes());
            System.out.println(f.substring(0, Math.min(400, f.length())));
        }
        System.out.println("ALL OK");
    }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
}
