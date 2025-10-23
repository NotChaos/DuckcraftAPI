package com.duckydeveloper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Message {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static Component pluginPrefix;
    private static Component insufficientPermissions;

    public static void init(Component prefix, Component insufficientPermissions) {
        Message.pluginPrefix = prefix;
        Message.insufficientPermissions = insufficientPermissions;
    }

    public static void invalid(Player player, String message) {
        invalid(player, message, true);
    }

    public static void invalid(Player player, String message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(Component.text(ChatColor.RED + message)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1, 1);
    }

    public static void invalid(Player player, Component message) {
        invalid(player, message, true);
    }

    public static void invalid(Player player, Component message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(message));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1, 1);
    }

    public static void invalid(CommandSender sender, String message) {
        invalid(sender, message, true);
    }

    public static void invalid(CommandSender sender, String message, boolean withPrefix) {
        if (sender instanceof Player player) {
            invalid(player, message, withPrefix);
            return;
        }

        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        sender.sendMessage(prefix.append(Component.text(ChatColor.RED + message)));
    }

    public static void invalid(CommandSender sender, Component message) {
        invalid(sender, message, true);
    }

    public static void invalid(CommandSender sender, Component message, boolean withPrefix) {
        if (sender instanceof Player player) {
            invalid(player, message, withPrefix);
            return;
        }

        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        sender.sendMessage(prefix.append(message));
    }

    public static void successful(Player player, String message) {
        successful(player, message, true);
    }

    public static void successful(Player player, String message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(Component.text(ChatColor.GREEN + message)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    public static void successful(Player player, Component message) {
        successful(player, message, true);
    }

    public static void successful(Player player, Component message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(message));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    public static void successful(CommandSender sender, String message) {
        successful(sender, message, true);
    }

    public static void successful(CommandSender sender, String message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(Component.text(ChatColor.GREEN + message)));
            return;
        }

        successful(player, message, withPrefix);
    }

    public static void successful(CommandSender sender, Component message) {
        successful(sender, message, true);
    }

    public static void successful(CommandSender sender, Component message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(message));
            return;
        }

        successful(player, message, withPrefix);
    }

    public static void restricted(Player player, String message) {
        restricted(player, message, true);
    }

    public static void restricted(Player player, String message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(Component.text(ChatColor.RED + message)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }

    public static void restricted(Player player, Component message) {
        restricted(player, message, true);
    }

    public static void restricted(Player player, Component message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(message));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
    }

    public static void restricted(CommandSender sender, String message) {
        restricted(sender, message, true);
    }

    public static void restricted(CommandSender sender, String message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(insufficientPermissions));
            return;
        }

        restricted(player, message, withPrefix);
    }

    public static void restricted(CommandSender sender, Component message) {
        restricted(sender, message, true);
    }

    public static void restricted(CommandSender sender, Component message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(message));
            return;
        }

        restricted(player, message, withPrefix);
    }

    public static void restricted(Player player) {
        restricted(player, insufficientPermissions, true);
    }

    public static void restricted(CommandSender sender) {
        restricted(sender, insufficientPermissions, true);
    }

    public static void important(Player player, String message) {
        important(player, message, true);
    }

    public static void important(Player player, String message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(Component.text(ChatColor.RED + message)));
        player.playSound(player.getLocation(), Sound.BLOCK_NETHERITE_BLOCK_STEP, 1, 1);
    }

    public static void important(Player player, Component message) {
        important(player, message, true);
    }

    public static void important(Player player, Component message, boolean withPrefix) {
        Component prefix = withPrefix ? pluginPrefix : Component.empty();
        player.sendMessage(prefix.append(message));
        player.playSound(player.getLocation(), Sound.BLOCK_NETHERITE_BLOCK_STEP, 1, 1);
    }

    public static void important(CommandSender sender, String message) {
        important(sender, message, true);
    }

    public static void important(CommandSender sender, String message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(Component.text(ChatColor.RED + message)));
            return;
        }

        important(player, message, withPrefix);
    }

    public static void important(CommandSender sender, Component message) {
        important(sender, message, true);
    }

    public static void important(CommandSender sender, Component message, boolean withPrefix) {
        if (!(sender instanceof Player player)) {
            Component prefix = withPrefix ? pluginPrefix : Component.empty();
            sender.sendMessage(prefix.append(message));
            return;
        }

        important(player, message, withPrefix);
    }

    @Deprecated
    public static String convertMessageToString(@Nullable String message) {
        String step1 = convertLegacyCodes(convertHexToLegacy(message));
        return convertLegacyCodes(step1);
    }

    public static String convertStringToLegacy(@Nullable String message) {
        return convertMessageToString(message);
    }

    private static String convertHexToLegacy(@Nullable String text) {
        if (text == null) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder stringBuilder = new StringBuilder();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            matcher.appendReplacement(stringBuilder, "§x§" + hexColor.charAt(0) + "§" + hexColor.charAt(1) + "§" + hexColor.charAt(2) + "§" + hexColor.charAt(3) + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5));
        }

        matcher.appendTail(stringBuilder);
        return stringBuilder.toString().replaceAll("&([0-9a-fk-or])", "§$1");
    }

    public static Component convertStringToComponent(String text) {
        String miniMessageText = convertLegacyCodes(text);

        return MiniMessage.miniMessage().deserialize(miniMessageText);
    }

    public static String convertLegacyCodes(@Nullable String message) {
        if (message == null) {
            return "";
        }

        Map<Character, String> legacyMap = Map.ofEntries(
                Map.entry('0', "black"),
                Map.entry('1', "dark_blue"),
                Map.entry('2', "dark_green"),
                Map.entry('3', "dark_aqua"),
                Map.entry('4', "dark_red"),
                Map.entry('5', "dark_purple"),
                Map.entry('6', "gold"),
                Map.entry('7', "gray"),
                Map.entry('8', "dark_gray"),
                Map.entry('9', "blue"),
                Map.entry('a', "green"),
                Map.entry('b', "aqua"),
                Map.entry('c', "red"),
                Map.entry('d', "light_purple"),
                Map.entry('e', "yellow"),
                Map.entry('f', "white"),
                Map.entry('k', "obfuscated"),
                Map.entry('l', "bold"),
                Map.entry('m', "strikethrough"),
                Map.entry('n', "underlined"),
                Map.entry('o', "italic"),
                Map.entry('r', "reset")
        );

        message = message.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        Pattern legacyPattern = Pattern.compile("[§&]([0-9a-frk-or])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = legacyPattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char code = matcher.group(1).toLowerCase().charAt(0);
            String tag = legacyMap.get(code);
            if (tag == null) {
                tag = "";
            }
            matcher.appendReplacement(sb, "<" + tag + ">");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static List<Component> convertStringListToComponentList(@Nullable List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }

        List<Component> components = new ArrayList<>();
        for (String message : messages) {
            components.add(convertStringToComponent(message));
        }
        return components;
    }

    public static List<String> convertStringListToLegacy(@Nullable List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }

        List<String> convertedMessages = new ArrayList<>();
        for (String message : messages) {
            convertedMessages.add(convertMessageToString(message));
        }
        return convertedMessages;
    }

    public static Component deserializeMiniMessage(String message) {
        return MiniMessage.miniMessage().deserialize(message);
    }

    public static List<Component> deserializeMiniMessageList(@Nullable List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }

        return messages.stream()
                .map(Message::deserializeMiniMessage)
                .collect(Collectors.toList());
    }
}