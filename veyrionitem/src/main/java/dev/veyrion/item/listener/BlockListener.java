package dev.veyrion.item.listener;

import dev.veyrion.item.content.ContentRegistry;
import dev.veyrion.item.content.ContentRegistry.BlockDef;
import dev.veyrion.item.content.Instruments;
import dev.veyrion.item.pack.BlockStates;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Custom blocks drawn through note block states. */
public final class BlockListener implements Listener {
    private final ContentRegistry reg;

    public BlockListener(ContentRegistry reg) { this.reg = reg; }

    private BlockDef customAt(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) return null;
        NoteBlock nb = (NoteBlock) b.getBlockData();
        String inst = Instruments.name(nb.getInstrument());
        return inst == null ? null : reg.blockAt(inst, nb.getNote().getId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = reg.idOf(e.getItemInHand());
        if (id == null) return;
        BlockDef def = reg.blocks.get(id);
        if (def == null) return;
        NoteBlock nb = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        nb.setInstrument(Instruments.bukkit(BlockStates.instrument(def.state())));
        nb.setNote(new Note(BlockStates.note(def.state())));
        nb.setPowered(false);
        e.getBlockPlaced().setBlockData(nb, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        BlockDef def = customAt(e.getBlock());
        if (def == null) return;
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            var item = reg.items.get(def.key());
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), reg.create(item, 1));
        }
    }

    /** Stops the server from recalculating the instrument (which would change the block's look). */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NOTE_BLOCK && customAt(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (customAt(e.getBlock()) != null) e.setCancelled(true);
    }

    /** Right click would retune the note block. Deny the block use, keep item use (placing against it). */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && customAt(e.getClickedBlock()) != null) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }
}
