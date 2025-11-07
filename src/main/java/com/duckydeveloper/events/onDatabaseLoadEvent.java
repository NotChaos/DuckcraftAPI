package com.duckydeveloper.events;

import com.duckydeveloper.DuckAPI;
import lombok.Getter;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class onDatabaseLoadEvent implements Listener {


    @Getter
    private static Plugin plugin;
    @Getter
    private static Logger logger;
    @Getter
    private static boolean debug;

    public onDatabaseLoadEvent(Plugin plugin, boolean debug) {
        onDatabaseLoadEvent.plugin = plugin;
        logger = plugin.getLogger();
        onDatabaseLoadEvent.debug = debug;
    }

    @EventHandler
    public void DatabaseLoadEvent(DatabaseLoadEvent e) {
        DuckAPI.setHeadApi(new HeadDatabaseAPI());
    }
}
