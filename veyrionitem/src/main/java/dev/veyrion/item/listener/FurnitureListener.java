package dev.veyrion.item.listener;

import dev.veyrion.item.VeyrionItemPlugin;
import dev.veyrion.item.content.ContentRegistry;
import dev.veyrion.item.content.ContentRegistry.FurnitureDef;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Furniture = one ItemDisplay (the look) + one Interaction (the hitbox), tied together by a group id.
 * Place: right click a block with the furniture item. Remove: punch it. Rotate: right click it.
 */
public final class FurnitureListener implements Listener {
    private final ContentRegistry reg;
    private final NamespacedKey furnitureKey;
    private final NamespacedKey groupKey;

    public FurnitureListener(VeyrionItemPlugin plugin, ContentRegistry reg) {
        this.reg = reg;
        this.furnitureKey = new NamespacedKey(plugin, "furniture");
        this.groupKey = new NamespacedKey(plugin, "group");
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = e.getItem();
        String id = reg.idOf(hand);
        FurnitureDef def = id == null ? null : reg.furniture.get(id);
        if (def == null || e.getClickedBlock() == null) return;
        e.setCancelled(true);

        Location loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if (!loc.getBlock().isPassable()) return;

        Player p = e.getPlayer();
        float yaw = Math.round(p.getLocation().getYaw() / 45f) * 45f + 180f;
        loc.setYaw(yaw);
        String group = UUID.randomUUID().toString();
        ItemStack shown = hand.clone();
        shown.setAmount(1);

        loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, def.scale() / 2f, 0), new AxisAngle4f(),
                    new Vector3f(def.scale()), new AxisAngle4f()));
            tag(d, id, group);
        });
        loc.getWorld().spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(def.width());
            i.setInteractionHeight(def.height());
            i.setResponsive(true);
            tag(i, id, group);
        });
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
    }

    @EventHandler
    public void onPunch(PrePlayerAttackEntityEvent e) {
        Entity target = e.getAttacked();
        String group = group(target);
        if (group == null) return;
        e.setCancelled(true);
        String id = target.getPersistentDataContainer().get(furnitureKey, PersistentDataType.STRING);
        for (Entity n : target.getNearbyEntities(2, 2, 2)) {
            if (group.equals(group(n))) n.remove();
        }
        target.remove();
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE && id != null && reg.items.containsKey(id)) {
            target.getWorld().dropItemNaturally(target.getLocation().add(0, 0.5, 0), reg.create(reg.items.get(id), 1));
        }
    }

    @EventHandler
    public void onRotate(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String group = group(e.getRightClicked());
        if (group == null) return;
        e.setCancelled(true);
        for (Entity n : e.getRightClicked().getNearbyEntities(2, 2, 2)) {
            if (n instanceof ItemDisplay d && group.equals(group(d))) d.setRotation(d.getLocation().getYaw() + 45f, 0);
        }
    }

    private void tag(Entity en, String id, String group) {
        var pdc = en.getPersistentDataContainer();
        pdc.set(furnitureKey, PersistentDataType.STRING, id);
        pdc.set(groupKey, PersistentDataType.STRING, group);
    }

    private String group(Entity en) {
        return en.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
    }
}
