package dev.veyrion.item.listener;

import dev.veyrion.item.content.ContentRegistry;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Items with "hat: true" can be worn on the head: right click, or click them onto the helmet slot. */
public final class HatListener implements Listener {
    private final ContentRegistry reg;

    public HatListener(ContentRegistry reg) { this.reg = reg; }

    private boolean isHat(ItemStack st) {
        String id = reg.idOf(st);
        var def = id == null ? null : reg.items.get(id);
        return def != null && def.hat();
    }

    private static boolean empty(ItemStack st) { return st == null || st.getType() == Material.AIR; }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getItem();
        if (!isHat(hand)) return;
        PlayerInventory inv = e.getPlayer().getInventory();
        if (!empty(inv.getHelmet())) return;
        e.setCancelled(true);
        ItemStack one = hand.clone();
        one.setAmount(1);
        inv.setHelmet(one);
        hand.setAmount(hand.getAmount() - 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getClickedInventory() instanceof PlayerInventory inv)) return;
        if (e.getSlotType() != InventoryType.SlotType.ARMOR || e.getSlot() != 39) return;
        ItemStack cursor = e.getCursor();
        if (!isHat(cursor)) return;
        e.setCancelled(true);
        ItemStack old = inv.getHelmet();
        if (cursor.getAmount() == 1) {
            inv.setHelmet(cursor.clone());
            e.getWhoClicked().setItemOnCursor(empty(old) ? null : old);
        } else if (empty(old)) {
            ItemStack one = cursor.clone();
            one.setAmount(1);
            inv.setHelmet(one);
            cursor.setAmount(cursor.getAmount() - 1);
            e.getWhoClicked().setItemOnCursor(cursor);
        }
    }
}
