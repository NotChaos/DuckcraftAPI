package com.duckydeveloper.events;


import com.duckydeveloper.DuckAPI;
import com.duckydeveloper.util.GUI;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

import lombok.Getter;

public class onPageChangeEvent implements Listener {

    @Getter
    private static Plugin plugin;
    @Getter
    private static Logger logger;
    @Getter
    private static boolean debug;

    public onPageChangeEvent(Plugin plugin, boolean debug) {
        onPageChangeEvent.plugin = plugin;
        logger = plugin.getLogger();
        onPageChangeEvent.debug = debug;
    }

    @EventHandler
    public void PageChangeEvent(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey actionKey = new NamespacedKey(plugin, DuckAPI.getPersistentDataKey() + "pageAction");
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        event.setCancelled(true);
        GUI gui = GUI.getByInventory(event.getInventory());
        if (gui == null) return;

        if (action.equals("next")) {
            gui.nextPage();
        } else if (action.equals("previous") || action.equals("prev")) {
            gui.previousPage();
        }

        if (debug) {
            logger.info("Changed GUI page to " + gui.getCurrentPage() + " using " + action);
        }
    }
}
