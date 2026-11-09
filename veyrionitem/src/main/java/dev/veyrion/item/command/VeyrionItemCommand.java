package dev.veyrion.item.command;

import dev.veyrion.item.VeyrionItemPlugin;
import dev.veyrion.item.pack.McVersion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class VeyrionItemCommand implements CommandExecutor, TabCompleter {
    private final VeyrionItemPlugin plugin;

    public VeyrionItemCommand(VeyrionItemPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("veyrionitem.admin")) { s.sendMessage("No permission."); return true; }
        if (a.length == 0) {
            s.sendMessage("/vi give <player> <ns:id> [amount] | menu <ns:id> [player] | list | report | reload | generate [version|all] | versions");
            return true;
        }
        var reg = plugin.registry();
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (a.length < 3) { s.sendMessage("Usage: /vi give <player> <ns:id> [amount]"); return true; }
                Player p = Bukkit.getPlayerExact(a[1]);
                var def = reg.items.get(a[2]);
                if (p == null || def == null) { s.sendMessage("Unknown player or item."); return true; }
                int amount = 1;
                if (a.length > 3) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); }
                    catch (NumberFormatException ex) { s.sendMessage("Amount must be a number."); return true; }
                }
                p.getInventory().addItem(reg.create(def, amount));
            }
            case "menu" -> {
                if (a.length < 2) { s.sendMessage("Usage: /vi menu <ns:id> [player]"); return true; }
                var m = reg.menus.get(a[1]);
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pl ? pl : null);
                if (m == null || p == null) { s.sendMessage("Unknown menu or player."); return true; }
                plugin.menus().open(p, m);
            }
            case "list" -> {
                s.sendMessage("Items: " + String.join(", ", reg.items.keySet()));
                s.sendMessage("Menus: " + String.join(", ", reg.menus.keySet()));
                s.sendMessage("Font images: " + String.join(", ", reg.fonts.keySet()));
            }
            case "report" -> {
                var notes = reg.importNotes;
                s.sendMessage("ItemsAdder-format files read: " + reg.importedFiles + ", notes: " + notes.size());
                int max = 40;
                for (int i = 0; i < Math.min(max, notes.size()); i++) s.sendMessage(" - " + notes.get(i));
                if (notes.size() > max) s.sendMessage(" ... " + (notes.size() - max) + " more, see the server log");
            }
            case "reload" -> { plugin.reload(); s.sendMessage("Reloaded. Run /vi generate to rebuild the packs."); }
            case "generate" -> plugin.generatePack(s, a.length > 1 ? a[1] : "all");
            case "versions" -> {
                List<String> l = new ArrayList<>();
                for (McVersion v : McVersion.ALL) l.add(v.name() + " (format " + v.packFormat() + ")");
                s.sendMessage(String.join(", ", l));
            }
            default -> s.sendMessage("Unknown subcommand.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("give", "menu", "list", "report", "reload", "generate", "versions"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("generate")) {
            out.add("all");
            McVersion.ALL.forEach(v -> out.add(v.name()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            out.addAll(plugin.registry().items.keySet());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("menu")) {
            out.addAll(plugin.registry().menus.keySet());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
