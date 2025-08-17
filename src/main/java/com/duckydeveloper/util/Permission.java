package com.duckydeveloper.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Permission {

    private static Plugin plugin;
    private static String permission;

    public static void init(Plugin plugin, String permission) {
        Permission.plugin = plugin;
        Permission.permission = permission;

    }

    public static boolean hasPermission(Player player, String permission) {
        return player.hasPermission(Permission.permission + "." + permission);
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player player) {
            return hasPermission(player, permission);
        }
        return sender.hasPermission(permission + "." + permission);
    }
}
