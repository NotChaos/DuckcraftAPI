package com.duckydeveloper.util;

import com.duckydeveloper.DuckAPI;
import lombok.Getter;
import lombok.Setter;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GUI {
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private int size;
    @Getter
    private final Inventory inventory;

    public GUI(@Nullable String title, @Nullable Integer size, @Nullable Inventory inventory) {
        this.title = title != null ? title : "GUI Title";
        this.size = size != null ? size : 27;
        if (inventory == null) {
            this.inventory = Bukkit.createInventory(null, this.size, Message.convertStringToLegacy(this.title));
        } else {
            this.inventory = inventory;
        }
    }

    public void setSlot(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    public ItemStack getSlot(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return null;
        return inventory.getItem(slot);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath) {
        if (DuckAPI.getDebug()) {
            DuckAPI.getPlugin().getLogger().info("[DuckAPI] Parsing GUI from config path: " + configPath);
        }

        Configuration config = DuckAPI.getPlugin().getConfig();

        int size;
        try {
            int cfgSize = config.getInt(configPath + ".Size", 0);
            if (cfgSize == 0) {
                int rows = config.getInt(configPath + ".Rows", 0);
                size = rows == 0 ? 27 : rows * 9;
            } else {
                size = cfgSize;
            }
        } catch (Exception ignored) {
            size = 27;
        }

        String title = config.getString(configPath + ".Title", "GUI Title");
        GUI gui = new GUI(title, size, null);

        ConfigurationSection section = config.getConfigurationSection(configPath);
        if (section == null) return CompletableFuture.completedFuture(gui);

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) continue;

            if (!itemSection.contains("Material") && !itemSection.contains("Slot") && !itemSection.contains("Name") && !itemSection.contains("Lore") && !itemSection.contains("HeadUUID") && !itemSection.contains("HeadDBId")) {
                continue;
            }

            int slot = itemSection.getInt("Slot", 0);
            String matName = itemSection.getString("Material", "AIR");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.AIR;
            if (mat == Material.AIR) continue;

            ItemStack item = new ItemStack(mat);
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) continue;

            String headUuidString = itemSection.getString("HeadUUID");
            UUID headUuid = null;

            if (headUuidString != null && !headUuidString.isEmpty() && mat == Material.PLAYER_HEAD) {
                try {
                    headUuid = UUID.fromString(headUuidString);
                    ((SkullMeta) itemMeta).setOwningPlayer(Bukkit.getOfflinePlayer(headUuid));
                } catch (Exception ignore) {
                }
            }

            if (DuckAPI.getEnableHeadAPIIntegration()) {
                HeadDatabaseAPI headAPI;

                headAPI = DuckAPI.getHeadApi();

                if (headAPI == null) {
                    if (DuckAPI.getDebug())
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] HeadAPI is not available; skipping HeadDB lookups.");
                }

                String headDbId = itemSection.getString("HeadDBId");

                if (DuckAPI.getDebug()) {
                    if (headUuid != null) {
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Setting player head for UUID: " + headUuid);
                    }
                    if (headDbId != null) {
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Setting HeadDB head for ID: " + headDbId);
                    }
                }

                if (mat == Material.PLAYER_HEAD) {
                    if (headAPI == null || headDbId == null || headDbId.isEmpty()) {
                        if (DuckAPI.getDebug()) {
                            DuckAPI.getPlugin().getLogger().info("[DuckAPI] HeadAPI is not enabled or HeadDBId is missing/empty, skipping HeadDB head setting.");
                        }
                    } else {
                        try {
                            ItemStack headItem = headAPI.getItemHead(headDbId);
                            if (headItem != null && headItem.getItemMeta() instanceof SkullMeta) {
                                SkullMeta headMeta = (SkullMeta) headItem.getItemMeta();
                                if (headMeta.getPlayerProfile() != null && itemMeta instanceof SkullMeta) {
                                    ((SkullMeta) itemMeta).setPlayerProfile(headMeta.getPlayerProfile());
                                }
                            }
                        } catch (Exception ex) {
                            if (DuckAPI.getDebug()) {
                                DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Failed to set HeadDB head for ID " + headDbId + ": " + ex.getMessage());
                            }
                        }
                    }
                }
            }

            itemMeta.getPersistentDataContainer().set(
                    new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "isGuiItem"),
                    PersistentDataType.BOOLEAN, true
            );
            itemMeta.setItemName(Message.convertStringToLegacy(itemSection.getString("Name", itemMeta.getDisplayName())));
            itemMeta.setLore(Message.convertStringListToLegacy(itemSection.getStringList("Lore")));
            item.setItemMeta(itemMeta);

            if (DuckAPI.getDebug()) {
                Bukkit.getLogger().info("[DuckAPI] Parsed GUI item at " + configPath + "." + key + " into slot " + slot);
                Bukkit.getLogger().info("[DuckAPI] Item Material: " + item.getType());
                Bukkit.getLogger().info("[DuckAPI] Item Name: " + itemMeta.getDisplayName());
                Bukkit.getLogger().info("[DuckAPI] Item Lore: " + itemMeta.getLore());
                if (headUuid != null) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Player head name: " + Bukkit.getOfflinePlayer(headUuid));
                }
            }

            if (slot >= 0 && slot < gui.getSize()) {
                gui.setSlot(slot, item);
            } else {
                if (DuckAPI.getDebug()) {
                    DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Skipping setting GUI item for out-of-bounds slot: " + slot + " (size=" + gui.getSize() + ")");
                }
            }
        }

        return CompletableFuture.completedFuture(gui);
    }
}