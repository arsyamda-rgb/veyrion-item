package dev.veyrion.item.content;

import dev.veyrion.item.VeyrionItemPlugin;
import dev.veyrion.item.imports.ItemsAdderImporter;
import dev.veyrion.item.imports.ResourceFolders;
import dev.veyrion.item.pack.BlockStates;
import dev.veyrion.item.pack.PackContent;
import dev.veyrion.item.pack.Spaces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads plugins/VeyrionItem/contents/&lt;namespace&gt;/ :
 *   configs/**.yml                         definitions
 *   resource_pack/assets/&lt;ns&gt;/...          textures, models, sounds (copied into every generated pack)
 */
public final class ContentRegistry {

    public record ItemDef(String ns, String id, String name, List<String> lore, Material base,
                          int cmd, String textureRef, String modelRef, String blockId, String furnitureId, boolean hat) {
        public String key() { return ns + ":" + id; }
    }

    public record BlockDef(String ns, String id, int state, String textureRef, String modelRef) {
        public String key() { return ns + ":" + id; }
    }

    public record FurnitureDef(String ns, String id, float scale, float width, float height) {
        public String key() { return ns + ":" + id; }
    }

    public record FontDef(String ns, String id, int codepoint, String fileRef, int ascent, int height, int advance) {
        public String key() { return ns + ":" + id; }
        public String glyph() { return new String(Character.toChars(codepoint)); }
    }

    public record SlotDef(String item, String command, boolean close) {}

    public record MenuDef(String ns, String id, int rows, String title, String background, int offset,
                          Map<Integer, SlotDef> slots) {
        public String key() { return ns + ":" + id; }
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final VeyrionItemPlugin plugin;
    private final NamespacedKey itemKey;
    public final Map<String, ItemDef> items = new LinkedHashMap<>();
    public final Map<String, BlockDef> blocks = new LinkedHashMap<>();
    public final Map<String, FurnitureDef> furniture = new LinkedHashMap<>();
    public final Map<String, FontDef> fonts = new LinkedHashMap<>();
    public final Map<String, MenuDef> menus = new LinkedHashMap<>();
    private final Map<String, BlockDef> blockByState = new HashMap<>();
    private final Map<String, Path> packFiles = new TreeMap<>();

    /** What the ItemsAdder importer understood / skipped. Shown by /vi report. */
    public final List<String> importNotes = new ArrayList<>();
    public int importedFiles;

    private YamlConfiguration ids;
    private File idsFile;
    private int nextCmd, nextCodepoint;

    public ContentRegistry(VeyrionItemPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "item");
    }

    public NamespacedKey itemKey() { return itemKey; }

    // ------------------------------------------------------------ loading

    public void load() {
        items.clear(); blocks.clear(); furniture.clear(); fonts.clear(); menus.clear();
        blockByState.clear(); packFiles.clear(); importNotes.clear(); importedFiles = 0;
        File data = new File(plugin.getDataFolder(), "data");
        data.mkdirs();
        idsFile = new File(data, "ids.yml");
        ids = YamlConfiguration.loadConfiguration(idsFile);
        nextCmd = ids.getInt("_next_cmd", 1000);
        nextCodepoint = ids.getInt("_next_codepoint", 0xE000);

        File root = new File(plugin.getDataFolder(), "contents");
        File[] namespaces = root.listFiles(File::isDirectory);
        if (namespaces != null) {
            Arrays.sort(namespaces);
            for (File nsDir : namespaces) loadNamespace(nsDir);
        }
        ids.set("_next_cmd", nextCmd);
        ids.set("_next_codepoint", nextCodepoint);
        try { ids.save(idsFile); } catch (IOException e) { warn("Could not save ids.yml: " + e); }
    }

    private void loadNamespace(File nsDir) {
        String folder = nsDir.getName();

        // resourcepack/<ns>/... (ItemsAdder) and resource_pack/assets/<ns>/... -> copied into the pack
        try {
            packFiles.putAll(ResourceFolders.collect(nsDir.toPath()));
        } catch (IOException e) { warn("cannot read resource folder of " + folder + ": " + e); }

        // configs/**.yml -> definitions
        Path cfg = nsDir.toPath().resolve("configs");
        if (!Files.isDirectory(cfg)) { warn("namespace '" + folder + "' has no configs/ folder"); return; }
        List<Path> ymls;
        try (Stream<Path> w = Files.walk(cfg)) {
            ymls = w.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        } catch (IOException e) { warn("cannot read " + cfg + ": " + e); return; }

        // ItemsAdder files declare their namespace in "info: namespace:", which may differ from the folder name
        // (assets live under assets/<that namespace>/).
        Map<Path, Map<String, Object>> docs = new LinkedHashMap<>();
        String declared = null;
        for (Path f : ymls) {
            Map<String, Object> doc = Yaml2Map.convert(YamlConfiguration.loadConfiguration(f.toFile()));
            docs.put(f, doc);
            String n = ItemsAdderImporter.namespaceOf(doc);
            if (n != null && declared == null) declared = n;
            else if (n != null && !n.equals(declared)) {
                importNotes.add(folder + ": files declare different namespaces ('" + declared + "' and '" + n + "'), using '" + declared + "'");
            }
        }
        final String ns = declared != null ? declared : folder;
        if (declared != null && !declared.equals(folder)) {
            importNotes.add(folder + ": namespace is '" + declared + "' (from info.namespace), not the folder name");
        }
        if (!ID.matcher(ns).matches()) { warn("namespace '" + ns + "' must match [a-z0-9_]+"); return; }

        for (Map.Entry<Path, Map<String, Object>> e : docs.entrySet()) {
            Map<String, Object> doc = e.getValue();
            YamlConfiguration y;
            if (ItemsAdderImporter.looksLikeItemsAdder(doc)) {
                String label = folder + "/" + cfg.relativize(e.getKey()).toString().replace(File.separatorChar, '/');
                var r = ItemsAdderImporter.convert(doc, label);
                importedFiles++;
                importNotes.addAll(r.notes());
                importNotes.add(label + ": ItemsAdder format -> " + r.items() + " items, " + r.blocks() + " blocks, "
                        + r.furniture() + " furniture, " + r.fonts() + " font images");
                y = new YamlConfiguration();
                for (Map.Entry<String, Object> sec : r.nativeDoc().entrySet()) {
                    if (sec.getValue() instanceof Map<?, ?> m) y.createSection(sec.getKey(), m);
                }
            } else {
                y = YamlConfiguration.loadConfiguration(e.getKey().toFile());
            }
            section(y, "items", (id, s) -> loadItem(ns, id, s, nsDir));
            section(y, "blocks", (id, s) -> loadBlock(ns, id, s, nsDir));
            section(y, "furniture", (id, s) -> loadFurniture(ns, id, s, nsDir));
            section(y, "font_images", (id, s) -> loadFont(ns, id, s, nsDir));
            section(y, "menus", (id, s) -> loadMenu(ns, id, s));
        }
    }

    private void section(YamlConfiguration y, String name, java.util.function.BiConsumer<String, ConfigurationSection> fn) {
        ConfigurationSection sec = y.getConfigurationSection(name);
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            if (!ID.matcher(id).matches()) { warn(name + "." + id + ": id must match [a-z0-9_]+"); continue; }
            fn.accept(id, s);
        }
    }

    /** Asset lookup in the collected pack files, e.g. asset("demo", "textures", "item/ruby.png"). */
    private Path asset(String ns, String kind, String rel) {
        return packFiles.get("assets/" + ns + "/" + kind + "/" + rel);
    }

    /** 'texture: item/ruby.png' -> must exist as assets/<ns>/textures/item/ruby.png, returns "ns:item/ruby". */
    private String textureRef(String ns, ConfigurationSection s, String what) {
        String rel = s.getString("texture");
        if (rel == null) return null;
        if (asset(ns, "textures", rel) == null) { warn(what + ": texture not found: textures/" + rel + " in namespace " + ns); return null; }
        return ns + ":" + (rel.endsWith(".png") ? rel.substring(0, rel.length() - 4) : rel);
    }

    /** 'model: item/chair' -> must exist as assets/<ns>/models/item/chair.json, returns "ns:item/chair". */
    private String modelRef(String ns, ConfigurationSection s, String what) {
        String rel = s.getString("model");
        if (rel == null) return null;
        if (rel.endsWith(".json")) rel = rel.substring(0, rel.length() - 5);
        if (asset(ns, "models", rel + ".json") == null) { warn(what + ": model not found: models/" + rel + ".json in namespace " + ns); return null; }
        return ns + ":" + rel;
    }

    private int stableInt(String path) {
        if (ids.contains(path)) return ids.getInt(path);
        int v = path.startsWith("cmd.") ? nextCmd++ : nextCodepoint++;
        ids.set(path, v);
        return v;
    }

    private void loadItem(String ns, String id, ConfigurationSection s, File nsDir) {
        String what = "item " + ns + ":" + id;
        String tex = textureRef(ns, s, what), mod = modelRef(ns, s, what);
        if (tex == null && mod == null) { warn(what + ": needs a valid 'texture' or 'model'"); return; }
        Material base = Material.matchMaterial(s.getString("base", "PAPER"));
        if (base == null || !base.isItem()) { warn(what + ": bad base material"); return; }
        items.put(ns + ":" + id, new ItemDef(ns, id, s.getString("display_name", id),
                s.getStringList("lore"), base, stableInt("cmd." + ns + ":" + id), tex, mod, null, null, s.getBoolean("hat", false)));
    }

    private void loadBlock(String ns, String id, ConfigurationSection s, File nsDir) {
        String key = ns + ":" + id, what = "block " + key;
        String tex = textureRef(ns, s, what), mod = modelRef(ns, s, what);
        if (tex == null && mod == null) { warn(what + ": needs a valid 'texture' or 'model'"); return; }
        int state;
        if (ids.contains("state." + key)) {
            state = ids.getInt("state." + key);
        } else {
            state = ids.getInt("_next_state", 0);
            if (state >= BlockStates.capacity()) { warn("out of note block states (" + BlockStates.capacity() + ")"); return; }
            ids.set("_next_state", state + 1);
            ids.set("state." + key, state);
        }
        BlockDef b = new BlockDef(ns, id, state, tex, mod);
        blocks.put(key, b);
        blockByState.put(BlockStates.instrument(state) + ":" + BlockStates.note(state), b);
        String itemModel = mod != null ? mod : ns + ":generated/block_" + id; // must match PackBuilder
        items.put(key, new ItemDef(ns, id, s.getString("display_name", id), s.getStringList("lore"),
                Material.NOTE_BLOCK, stableInt("cmd." + key), null, itemModel, key, null, false));
    }

    private void loadFurniture(String ns, String id, ConfigurationSection s, File nsDir) {
        String key = ns + ":" + id, what = "furniture " + key;
        String tex = textureRef(ns, s, what), mod = modelRef(ns, s, what);
        if (tex == null && mod == null) { warn(what + ": needs a valid 'texture' or 'model'"); return; }
        furniture.put(key, new FurnitureDef(ns, id, (float) s.getDouble("scale", 1.0),
                (float) s.getDouble("hitbox.width", 1.0), (float) s.getDouble("hitbox.height", 1.0)));
        Material base = Objects.requireNonNullElse(Material.matchMaterial(s.getString("base", "PAPER")), Material.PAPER);
        items.put(key, new ItemDef(ns, id, s.getString("display_name", id), s.getStringList("lore"),
                base, stableInt("cmd." + key), tex, mod, null, key, false));
    }

    private void loadFont(String ns, String id, ConfigurationSection s, File nsDir) {
        String what = "font_image " + ns + ":" + id;
        String rel = s.getString("texture");
        if (rel == null) { warn(what + ": missing 'texture'"); return; }
        Path f = asset(ns, "textures", rel);
        if (f == null) { warn(what + ": texture not found: textures/" + rel); return; }
        int height = s.getInt("height", 9);
        int ascent = s.getInt("ascent", Math.min(height - 1, 8));
        int advance = height;
        try {
            var img = ImageIO.read(f.toFile());
            if (img != null) advance = Math.round(img.getWidth() * (float) height / img.getHeight()) + 1;
        } catch (IOException e) { warn("cannot read " + f); }
        fonts.put(ns + ":" + id, new FontDef(ns, id, stableInt("cp." + ns + ":" + id),
                ns + ":" + rel, ascent, height, advance));
    }

    private void loadMenu(String ns, String id, ConfigurationSection s) {
        Map<Integer, SlotDef> slots = new LinkedHashMap<>();
        ConfigurationSection ss = s.getConfigurationSection("slots");
        if (ss != null) {
            for (String k : ss.getKeys(false)) {
                try {
                    slots.put(Integer.parseInt(k), new SlotDef(ss.getString(k + ".item"),
                            ss.getString(k + ".command"), ss.getBoolean(k + ".close", false)));
                } catch (NumberFormatException e) { warn("menu " + id + ": bad slot " + k); }
            }
        }
        menus.put(ns + ":" + id, new MenuDef(ns, id, Math.max(1, Math.min(6, s.getInt("rows", 3))),
                s.getString("title", id), s.getString("background"), s.getInt("offset", -8), slots));
    }

    private void warn(String msg) { plugin.getLogger().warning(msg); }

    // ------------------------------------------------------------ runtime helpers

    public ItemStack create(ItemDef d, int amount) {
        ItemStack st = new ItemStack(d.base(), amount);
        ItemMeta m = st.getItemMeta();
        m.displayName(MM.deserialize(d.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (!d.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String l : d.lore()) lore.add(MM.deserialize(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            m.lore(lore);
        }
        m.setCustomModelData(d.cmd());
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, d.key());
        st.setItemMeta(m);
        return st;
    }

    /** "ns:id" stored on the item, or null when it is not a VeyrionItem item. */
    public String idOf(ItemStack st) {
        if (st == null || !st.hasItemMeta()) return null;
        return st.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public BlockDef blockAt(String instrumentName, int note) {
        return blockByState.get(instrumentName + ":" + note);
    }

    public String glyphSequence(MenuDef m) {
        FontDef bg = m.background() == null ? null : fonts.get(m.background());
        if (bg == null) return "";
        return Spaces.of(m.offset()) + bg.glyph() + Spaces.of(-(m.offset() + bg.advance()));
    }

    public PackContent toPackContent(String description) {
        List<PackContent.ModelEntry> models = new ArrayList<>();
        for (ItemDef d : items.values()) {
            models.add(new PackContent.ModelEntry(d.ns(), d.id(), d.base().getKey().getKey(), d.cmd(),
                    d.textureRef(), d.modelRef()));
        }
        List<PackContent.BlockEntry> bl = new ArrayList<>();
        for (BlockDef b : blocks.values()) bl.add(new PackContent.BlockEntry(b.ns(), b.id(), b.state(), b.textureRef(), b.modelRef()));
        List<PackContent.FontImageEntry> fl = new ArrayList<>();
        for (FontDef f : fonts.values()) fl.add(new PackContent.FontImageEntry(f.ns(), f.id(), f.codepoint(), f.fileRef(), f.ascent(), f.height()));
        return new PackContent(description, new TreeMap<>(packFiles), models, bl, fl);
    }
}
