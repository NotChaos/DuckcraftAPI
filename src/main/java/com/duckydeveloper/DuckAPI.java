package com.duckydeveloper;

import com.duckydeveloper.events.onDatabaseLoadEvent;
import com.duckydeveloper.events.onInventoryClickEvent;
import com.duckydeveloper.util.Message;
import com.duckydeveloper.util.Permission;
import lombok.Getter;
import lombok.Setter;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.kyori.adventure.text.Component;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

public class DuckAPI {
    @Getter
    private static final Properties languageConfig = new Properties();
    private static final Map<String, Component> messageComponentMap = new HashMap<>();
    @Getter
    private static JavaPlugin plugin;
    private static String permissionName;
    @Getter
    private static Boolean debug;
    private static Integer bstatsId;
    @Getter
    private static Metrics bstatsMetrics;
    @Getter
    private static String persistentDataKey;
    private static Boolean blockGuiItemInteractions;
    @Getter
    private static Boolean enableHeadAPIIntegration = false;
    private static CompletableFuture<HeadDatabaseAPI> headApiFuture = new CompletableFuture<>();

    public static void setHeadApi(HeadDatabaseAPI api) {
        if (headApiFuture == null) headApiFuture = new CompletableFuture<>();
        headApiFuture.complete(api);
    }

    public static HeadDatabaseAPI getHeadApi() {
        try {
            return headApiFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (CancellationException ignore) {
            return null;
        }
    }

    public static void init(DuckAPIBuilder builder) {
        plugin = builder.getPlugin();
        permissionName = builder.getPermissionName();
        bstatsId = builder.getBstatsId();
        persistentDataKey = builder.getPersistentDataKey();
        blockGuiItemInteractions = builder.getBlockGuiItemInteractions();
        enableHeadAPIIntegration = builder.getEnableHeadAPIIntegration();

        if (bstatsId == null) {
            bstatsId = 0;
        }

        debug = builder.getDebug();

        if (debug == null) {
            debug = false;
        }

        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        initLanguage();
        initUtils();
        initBstats();
        initEvents();

        if (persistentDataKey == null || persistentDataKey.isEmpty()) {
            persistentDataKey = "duckapi_data";
            plugin.getLogger().warning("Persistent Data Key is not set. Defaulting to 'duckapi_data'.");
        }
    }

    public static class DuckAPIBuilder {
        @Getter
        @Setter
        private JavaPlugin plugin;
        @Getter
        @Setter
        private String permissionName;
        @Getter
        @Setter
        private Boolean debug;
        @Getter
        @Setter
        private Integer bstatsId;
        @Getter
        @Setter
        private String persistentDataKey;
        @Getter
        @Setter
        @NotNull
        private Boolean blockGuiItemInteractions = true;
        @Getter
        @Setter
        @NotNull
        private Boolean enableHeadAPIIntegration = false;
    }

    private static void initLanguage() {
        Properties defaultMessages = new Properties();
        File dataFolder = plugin.getDataFolder();
        File messagesFile = new File(dataFolder, "messages.properties");

        try (InputStream defaultInput = plugin.getResource("messages.properties")) {
            if (defaultInput != null) {
                defaultMessages.load(defaultInput);
            } else {
                plugin.getLogger().warning("messages.properties has not been found in the jar!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading standard messages.properties: " + e.getMessage());
        }

        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.properties")) {
                if (in == null) {
                    plugin.getLogger().warning("messages.properties not in the jar");
                } else {
                    try (OutputStream out = new FileOutputStream(messagesFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        plugin.getLogger().info("messages.properties has been created in the resource folder.");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Error while copying messages.properties: " + e.getMessage());
            }
        }

        try (InputStream input = new FileInputStream(messagesFile)) {
            languageConfig.clear();
            languageConfig.load(input);
            plugin.getLogger().info("messages.properties loaded");
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading messages.properties: " + e.getMessage());
        }

        boolean updated = false;
        for (String key : defaultMessages.stringPropertyNames()) {
            if (!languageConfig.containsKey(key)) {
                languageConfig.setProperty(key, defaultMessages.getProperty(key));
                updated = true;
            }
        }

        if (updated) {
            try (OutputStream out = new FileOutputStream(messagesFile)) {
                languageConfig.store(out, "Updated: missing default values added");
                plugin.getLogger().info("messages.properties has been updated with new default values.");
            } catch (IOException e) {
                plugin.getLogger().severe("Error saving messages.properties: " + e.getMessage());
            }
        }

        messageComponentMap.clear();
        for (String key : languageConfig.stringPropertyNames()) {
            String value = languageConfig.getProperty(key);
            messageComponentMap.put(key, Message.convertStringToComponent(value));
        }
    }

    private static void initUtils() {
        if (permissionName == null || permissionName.isEmpty()) {
            plugin.getLogger().warning("Permission name is not set. Defaulting to 'duckapi'.");
            permissionName = "duckapi";
        }

        Message.init(getLanguageComponent("Prefix"), getLanguageComponent("InsufficientPermissions"));
        Permission.init(plugin, permissionName);
    }

    private static void initBstats() {
        if (bstatsId == null || bstatsId <= 0) {
            plugin.getLogger().warning("BStats ID is not set or invalid. Metrics will not be initialized.");
            return;
        }

        bstatsMetrics = new Metrics(plugin, bstatsId);
    }

    public static Component getLanguageComponent(String key) {
        return messageComponentMap.get(key);
    }

    public static String getLanguageString(String key) {
        return languageConfig.getProperty(key);
    }

    public static void reloadConfiguration() {
        initLanguage();
        initUtils();
    }

    private static void initEvents() {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new onInventoryClickEvent(plugin, debug, blockGuiItemInteractions), plugin);

        if (pm.isPluginEnabled("HeadDatabase") && enableHeadAPIIntegration) {
            pm.registerEvents(new onDatabaseLoadEvent(plugin, debug), plugin);
            plugin.getLogger().info("HeadDatabase integration enabled.");
        } else {
            enableHeadAPIIntegration = false;
        }
    }
}
