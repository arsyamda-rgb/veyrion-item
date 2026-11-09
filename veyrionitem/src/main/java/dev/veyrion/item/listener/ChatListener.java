package dev.veyrion.item.listener;

import dev.veyrion.item.content.ContentRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Turns :heart: style placeholders into font images in chat. */
public final class ChatListener implements Listener {
    private final ContentRegistry reg;

    public ChatListener(ContentRegistry reg) { this.reg = reg; }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!e.getPlayer().hasPermission("veyrionitem.fontimage")) return;
        Component msg = e.message();
        for (ContentRegistry.FontDef f : reg.fonts.values()) {
            String glyph = f.glyph();
            msg = msg.replaceText(b -> b.matchLiteral(":" + f.id() + ":").replacement(glyph));
            msg = msg.replaceText(b -> b.matchLiteral(":" + f.key() + ":").replacement(glyph));
        }
        e.message(msg);
    }
}
