package com.duckydeveloper.events;

import com.duckydeveloper.DuckAPI;
import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class onInventoryClickEvent implements Listener {


    @Getter
    private static Plugin plugin;
    @Getter
    private static Logger logger;
    @Getter
    private static boolean debug;
    @Getter
    private static boolean blockGuiItemInteractions;

    public onInventoryClickEvent(Plugin plugin, boolean debug, boolean blockGuiItemInteractions) {
        onInventoryClickEvent.plugin = plugin;
        logger = plugin.getLogger();
        onInventoryClickEvent.debug = debug;
        onInventoryClickEvent.blockGuiItemInteractions = blockGuiItemInteractions;
    }

    @EventHandler
    public void InventoryClickEventEvent(InventoryClickEvent e) {
        if (blockGuiItemInteractions) {
            try {
                Boolean isGuiItem = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, DuckAPI.getPersistentDataKey() + "isGuiItem"), PersistentDataType.BOOLEAN);
                if (isGuiItem != null && isGuiItem) {
                    e.setCancelled(true);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
