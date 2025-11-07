package com.duckydeveloper.util;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record GUIItem(@Getter @Nullable String name, @Getter @Nullable String[] lore, @Getter @Nullable Integer slot,
                      @Getter @Nullable Material material, @Nullable UUID playerHeadUuid) {

    public ItemStack getItemStack() {
        ItemStack itemStack = new ItemStack(Material.AIR);
        ItemMeta itemMeta = itemStack.getItemMeta();

        if (name != null) {
            itemMeta.setDisplayName(Message.convertStringToLegacy(name));
        }

        if (material != null && Material.getMaterial(material.toString()) != null) {
            itemStack.setType(Material.getMaterial(material.toString()));
        }

        itemStack.setItemMeta(itemMeta);
        itemMeta = itemStack.getItemMeta();

        if (itemMeta instanceof SkullMeta && playerHeadUuid != null) {
            ((SkullMeta) itemMeta).setOwningPlayer(Bukkit.getOfflinePlayer(playerHeadUuid));
        }

        if (lore != null) {
            itemMeta.setLore(Message.convertStringListToLegacy(lore));
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}