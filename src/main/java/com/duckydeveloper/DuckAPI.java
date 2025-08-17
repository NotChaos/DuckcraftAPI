package com.duckydeveloper;

import com.duckydeveloper.util.Message;
import com.duckydeveloper.util.Permission;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DuckAPI {
    @Getter
    private static final Properties languageConfig = new Properties();
    private static final Map<String, Component> messageComponentMap = new HashMap<>();
    private static JavaPlugin plugin;
    private static String permissionName;
    private static Boolean debug;
    private static Integer bstatsId;
    @Getter
    private static Metrics bstatsMetrics;

    public static void init(DuckAPIBuilder builder) {
        plugin = builder.getPlugin();
        permissionName = builder.getPermissionName();

        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        initLanguage();
        initUtils();
        initBstats();
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
        private boolean debug;
        @Getter
        @Setter
        private int bstatsId;
    }

    private static void initLanguage() {
        Properties defaultMessages = new Properties();
        File dataFolder = plugin.getDataFolder();
        File messagesFile = new File(dataFolder, "messages.properties");

        try (InputStream defaultInput = plugin.getResource("messages.properties")) {
            if (defaultInput != null) {
                defaultMessages.load(defaultInput);
            } else {
                plugin.getLogger().warning("messages.properties (Standard) nicht im JAR gefunden!");
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

        Message.init(getLanguageComponent("Prefix"), getLanguageComponent("InsufficientPermissions"), plugin.getLogger());
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
}
