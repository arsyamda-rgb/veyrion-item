package dev.veyrion.item.gui;

import dev.veyrion.item.content.ContentRegistry;
import dev.veyrion.item.content.ContentRegistry.MenuDef;
import dev.veyrion.item.content.ContentRegistry.SlotDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Chest menus whose title carries a font-image background. */
public final class MenuService implements Listener {
    private final ContentRegistry reg;

    public MenuService(ContentRegistry reg) { this.reg = reg; }

    private static final class Holder implements InventoryHolder {
        final MenuDef def;
        Inventory inv;
        Holder(MenuDef def) { this.def = def; }
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p, MenuDef m) {
        Holder h = new Holder(m);
        Component title = Component.text(reg.glyphSequence(m)).append(MiniMessage.miniMessage().deserialize(m.title()));
        h.inv = Bukkit.createInventory(h, m.rows() * 9, title);
        m.slots().forEach((slot, def) -> {
            if (slot < 0 || slot >= m.rows() * 9 || def.item() == null) return;
            var item = reg.items.get(def.item());
            if (item != null) h.inv.setItem(slot, reg.create(item, 1));
        });
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        SlotDef s = h.def.slots().get(e.getSlot());
        if (s == null || !(e.getWhoClicked() instanceof Player p)) return;
        if (s.command() != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s.command().replace("%player%", p.getName()));
        }
        if (s.close()) p.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
