package dev.veyrion.item;

import dev.veyrion.item.command.VeyrionItemCommand;
import dev.veyrion.item.content.ContentRegistry;
import dev.veyrion.item.gui.MenuService;
import dev.veyrion.item.listener.BlockListener;
import dev.veyrion.item.listener.ChatListener;
import dev.veyrion.item.listener.FurnitureListener;
import dev.veyrion.item.listener.HatListener;
import dev.veyrion.item.pack.McVersion;
import dev.veyrion.item.pack.PackGenerator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class VeyrionItemPlugin extends JavaPlugin {
    private ContentRegistry registry;
    private MenuService menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        extractDemoContent();
        registry = new ContentRegistry(this);
        registry.load();
        menus = new MenuService(registry);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new BlockListener(registry), this);
        pm.registerEvents(new FurnitureListener(this, registry), this);
        pm.registerEvents(new ChatListener(registry), this);
        pm.registerEvents(new HatListener(registry), this);
        pm.registerEvents(menus, this);

        var cmd = new VeyrionItemCommand(this);
        getCommand("vi").setExecutor(cmd);
        getCommand("vi").setTabCompleter(cmd);

        getLogger().info("Loaded " + registry.items.size() + " items, " + registry.blocks.size() + " blocks, "
                + registry.furniture.size() + " furniture, " + registry.fonts.size() + " font images, "
                + registry.menus.size() + " menus");
        if (registry.importedFiles > 0) {
            getLogger().info("Read " + registry.importedFiles + " ItemsAdder-format file(s), "
                    + registry.importNotes.size() + " note(s). Run /vi report to see what was skipped.");
            registry.importNotes.forEach(n -> getLogger().info("[import] " + n));
        }
        if (getConfig().getBoolean("pack.auto-generate", true)) generatePack(null, "all");
    }

    public ContentRegistry registry() { return registry; }
    public MenuService menus() { return menus; }

    public void reload() {
        reloadConfig();
        registry.load();
        registry.importNotes.forEach(n -> getLogger().info("[import] " + n));
    }

    /** Versions to build, from "all" or a single version name. */
    public List<McVersion> versionsFor(String arg) {
        if (arg == null || arg.equalsIgnoreCase("all")) {
            return McVersion.range(getConfig().getString("pack.from", "1.21.1"), getConfig().getString("pack.to", "1.21.11"));
        }
        return List.of(McVersion.parse(arg));
    }

    /** Runs off the main thread: reads textures and writes one zip per version. */
    public void generatePack(CommandSender who, String versionArg) {
        final List<McVersion> versions;
        try {
            versions = versionsFor(versionArg);
        } catch (IllegalArgumentException ex) {
            if (who != null) who.sendMessage("Unknown version. Use /vi versions");
            return;
        }
        var content = registry.toPackContent(getConfig().getString("pack.description", "VeyrionItem pack"));
        File out = new File(getDataFolder(), "output");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                var results = PackGenerator.generate(content, versions, out.toPath());
                Bukkit.getScheduler().runTask(this, () -> {
                    String msg = "Generated " + results.size() + " pack(s) in " + out.getPath() + " (see manifest.txt)";
                    getLogger().info(msg);
                    if (who != null) who.sendMessage(msg);
                });
            } catch (Exception ex) {
                getLogger().severe("Pack generation failed: " + ex);
                if (who != null) Bukkit.getScheduler().runTask(this, () -> who.sendMessage("Pack generation failed: " + ex.getMessage()));
            }
        });
    }

    /** First start only: copy the demo namespace so the contents/ layout is visible. */
    private void extractDemoContent() {
        if (new File(getDataFolder(), "contents").isDirectory()) return;
        String base = "contents/demo/";
        String tex = "resource_pack/assets/demo/textures/";
        for (String f : List.of("configs/demo.yml", tex + "item/ruby.png", tex + "item/armchair.png",
                tex + "block/ruby_block.png", tex + "font/heart.png", tex + "gui/shop_bg.png")) {
            saveResource(base + f, false);
        }
    }
}
