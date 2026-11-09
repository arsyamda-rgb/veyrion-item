package dev.veyrion.item.pack;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Everything the generator needs. Pure data, so it can be tested without a server. */
public record PackContent(String description,
                          Map<String, Path> files,
                          List<ModelEntry> models,
                          List<BlockEntry> blocks,
                          List<FontImageEntry> fonts) {

    /**
     * files: every file found in contents/&lt;ns&gt;/resource_pack/, keyed by its path inside the pack
     *        (e.g. "assets/demo/textures/item/ruby.png"). Copied into every generated pack.
     *
     * Locations below are resource locations such as "demo:item/ruby".
     */

    /**
     * An item model reachable through custom_model_data.
     * textureRef: flat 2D item, the model is generated.   modelRef: your own model, used as is.
     */
    public record ModelEntry(String ns, String id, String base, int cmd, String textureRef, String modelRef) {}

    /** A custom block: cube with one texture on every face, or your own model. */
    public record BlockEntry(String ns, String id, int state, String textureRef, String modelRef) {}

    /** A bitmap glyph in the default font. fileRef example: "demo:font/heart.png". */
    public record FontImageEntry(String ns, String id, int codepoint, String fileRef, int ascent, int height) {}
}
