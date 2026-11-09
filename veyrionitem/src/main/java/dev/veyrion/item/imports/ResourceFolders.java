package dev.veyrion.item.imports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Finds the asset files of a contents/&lt;namespace&gt;/ folder and maps them to their path inside a resource pack.
 *
 *  resourcepack/&lt;ns&gt;/models/item/x.json   (ItemsAdder layout)  -> assets/&lt;ns&gt;/models/item/x.json
 *  resource_pack/assets/&lt;ns&gt;/...           (VeyrionItem layout)  -> assets/&lt;ns&gt;/...
 *
 * A top-level "assets" folder inside resourcepack/ is kept as is. pack.mcmeta / pack.png at the root are skipped.
 */
public final class ResourceFolders {
    private ResourceFolders() {}

    public static Map<String, Path> collect(Path nsDir) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        walk(nsDir.resolve("resource_pack"), false, out);
        walk(nsDir.resolve("resourcepack"), true, out);
        return out;
    }

    private static void walk(Path root, boolean wrap, Map<String, Path> out) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> w = Files.walk(root)) {
            for (Path f : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                if (wrap) {
                    if (rel.equals("pack.mcmeta") || rel.equals("pack.png")) continue;
                    if (!rel.startsWith("assets/")) rel = "assets/" + rel;
                }
                out.put(rel, f);
            }
        }
    }
}
