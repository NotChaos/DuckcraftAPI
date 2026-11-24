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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

public class GUI {
    @Getter
    private final Inventory inventory;
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private int size;
    @Getter
    private String configPath;
    @Getter
    private ItemStack[] paginatedItems = new ItemStack[0];
    @Getter
    private int[] paginatedSlots = new int[0];
    @Getter
    private int currentPage;
    @Getter
    private int pageSize;
    @Getter
    private int pageCount;
    @Getter
    private boolean infinitePagination;

    private final Map<Integer, ItemStack> fixedSlots = new HashMap<>();
    private static final Map<Inventory, GUI> OPEN_GUIS = new WeakHashMap<>();

    public static GUI getByInventory(@Nullable Inventory inventory) {
        if (inventory == null) return null;
        synchronized (OPEN_GUIS) {
            return OPEN_GUIS.get(inventory);
        }
    }

    public GUI(@Nullable String title, @Nullable Integer size, @Nullable Inventory inventory) {
        this.title = title != null ? title : "GUI Title";
        this.size = size != null ? size : 27;
        if (inventory == null) {
            this.inventory = Bukkit.createInventory(null, this.size, Message.convertStringToLegacy(this.title));
        } else {
            this.inventory = inventory;
        }
        synchronized (OPEN_GUIS) {
            OPEN_GUIS.put(this.inventory, this);
        }
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath) {
        return parseConfigInternal(configPath, null, null, 0);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items) {
        return parseConfigInternal(configPath, items, null, 0);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items, int page) {
        return parseConfigInternal(configPath, items, null, page);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items, @Nullable int[] slots) {
        return parseConfig(configPath, items, slots, 0);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items, @Nullable int[] slots, int page) {
        return parseConfigInternal(configPath, items, slots, page);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable int[] slots, @Nullable ItemStack[] items) {
        return parseConfig(configPath, items, slots, 0);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable int[] slots, @Nullable ItemStack[] items, int page) {
        return parseConfig(configPath, items, slots, page);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items, @Nullable List<Integer> slots) {
        return parseConfigInternal(configPath, items, toSlotArray(slots), 0);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable ItemStack[] items, @Nullable List<Integer> slots, int page) {
        return parseConfigInternal(configPath, items, toSlotArray(slots), page);
    }

    public static CompletableFuture<GUI> parseConfig(@NotNull String configPath, @Nullable List<ItemStack> items) {
        return parseConfigInternal(configPath, items == null ? null : items.toArray(ItemStack[]::new), null, 0);
    }

    private static int[] toSlotArray(@Nullable List<Integer> slots) {
        if (slots == null) return null;
        int[] result = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) result[i] = slots.get(i) == null ? -1 : slots.get(i);
        return result;
    }

    private static CompletableFuture<GUI> parseConfigInternal(@NotNull String configPath, @Nullable ItemStack[] items, @Nullable int[] slots, int page) {
        boolean isDebug = DuckAPI.getDebug();

        if (isDebug) {
            DuckAPI.getPlugin().getLogger().info("[DuckAPI] Parsing GUI from config path: " + configPath);
        }

        Configuration config = DuckAPI.getPlugin().getConfig();

        int size;
        try {
            int cfgSize = config.getInt(configPath + ".Size", 0);
            if (cfgSize == 0) {
                int rows = config.getInt(configPath + ".Rows", 0);
                size = rows == 0 ? 27 : rows * 9;
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Computed size from Rows: rows=" + rows + " -> size=" + size);
                }
            } else {
                size = cfgSize;
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Using configured Size: " + cfgSize);
                }
            }
        } catch (Exception ignored) {
            size = 27;
            if (isDebug) {
                DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Error reading size from config, falling back to default size=27");
            }
        }

        String title = config.getString(configPath + ".Title", "GUI Title");
        if (isDebug) {
            DuckAPI.getPlugin().getLogger().info("[DuckAPI] GUI title: " + title);
        }
        GUI gui = new GUI(title, size, null);
        gui.configPath = configPath;
        gui.paginatedItems = items == null ? new ItemStack[0] : Arrays.copyOf(items, items.length);
        if (isDebug) {
            DuckAPI.getPlugin().getLogger().info("[DuckAPI] Created GUI with size=" + gui.getSize() + " title=\"" + gui.getTitle() + "\"");
        }

        ConfigurationSection section = config.getConfigurationSection(configPath);
        if (section == null) {
            if (isDebug) {
                DuckAPI.getPlugin().getLogger().info("[DuckAPI] No configuration section found at: " + configPath + " - returning empty GUI");
            }
            return CompletableFuture.completedFuture(gui);
        }

        if (slots == null || slots.length == 0) {
            slots = findPaginationSlots(configPath, section);
        }
        gui.paginatedSlots = slots == null ? new int[0] : Arrays.stream(slots).filter(s -> s >= 0 && s < gui.getSize()).distinct().toArray();
        gui.pageSize = gui.paginatedSlots.length;
        gui.pageCount = gui.pageSize == 0 || gui.paginatedItems.length == 0
                ? 1
                : Math.max(1, (int) Math.ceil((double) gui.paginatedItems.length / gui.pageSize));
        ConfigurationSection paginationConfig = section.getConfigurationSection("Pagination");
        gui.infinitePagination = section.getBoolean("InfinitePagination",
                paginationConfig != null ? paginationConfig.getBoolean("Infinite", false)
                        : section.getBoolean("Pagination.Infinite", false));
        gui.currentPage = gui.normalizePage(page);

        // Sequence future to ensure we process entries one-by-one and wait for head-owner/profile application
        CompletableFuture<Void> sequence = CompletableFuture.completedFuture(null);

        LinkedHashMap<String, ConfigurationSection> itemSections = findRenderableItemSections(section);
        for (Map.Entry<String, ConfigurationSection> entry : itemSections.entrySet()) {
            String key = entry.getKey();
            ConfigurationSection itemSection = entry.getValue();
            if (itemSection == null) {
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Skipping key " + configPath + "." + key + " because it's not a section");
                }
                continue;
            }

            if (!itemSection.contains("Material") && !itemSection.contains("Slot") && !itemSection.contains("Slots") && !itemSection.contains("Name") && !itemSection.contains("Lore") && !itemSection.contains("HeadUUID") && !itemSection.contains("HeadDBId")) {
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Skipping " + configPath + "." + key + " - no relevant fields found");
                }
                continue;
            }

            int[] itemSlots = findItemSlots(itemSection);
            String matName = itemSection.getString("Material", "AIR");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Material '" + matName + "' not recognized, defaulting to AIR for " + configPath + "." + key);
                }
                mat = Material.AIR;
            }
            if (mat == Material.AIR) {
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Skipping creation of AIR item for " + configPath + "." + key);
                }
                continue;
            }

            if (isDebug) {
                DuckAPI.getPlugin().getLogger().info("[DuckAPI] Creating item for " + configPath + "." + key + " with material " + mat);
            }
            ItemStack item = new ItemStack(mat);
            ItemMeta itemMeta = item.getItemMeta();

            if (itemMeta == null) {
                if (isDebug) {
                    DuckAPI.getPlugin().getLogger().warning("[DuckAPI] ItemMeta null for material " + mat + " at " + configPath + "." + key);
                }
                continue;
            }

            String headUuidString = itemSection.getString("HeadUUID");
            UUID headUuid = null;

            if (headUuidString != null && !headUuidString.isEmpty() && mat == Material.PLAYER_HEAD) {
                try {
                    headUuid = UUID.fromString(headUuidString);
                    ((SkullMeta) itemMeta).setOwningPlayer(Bukkit.getOfflinePlayer(headUuid));
                    if (isDebug) {
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Set owning player for player head: " + headUuid);
                    }
                } catch (Exception e) {
                    if (isDebug) {
                        DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Failed to parse/set HeadUUID '" + headUuidString + "' for " + configPath + "." + key + ": " + e.getMessage());
                    }
                }
            }

            itemMeta.getPersistentDataContainer().set(
                    new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "isGuiItem"),
                    PersistentDataType.BOOLEAN, true
            );
            String pageAction = itemSection.getString("PageAction");
            if (pageAction == null) {
                String normalizedKey = key.replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
                if (normalizedKey.equals("previouspage") || normalizedKey.equals("previous") || normalizedKey.equals("prev")) {
                    pageAction = "previous";
                } else if (normalizedKey.equals("nextpage") || normalizedKey.equals("next")) {
                    pageAction = "next";
                }
            }
            if (pageAction != null) {
                String normalizedAction = pageAction.trim().toLowerCase(java.util.Locale.ROOT);
                if (normalizedAction.equals("previous") || normalizedAction.equals("prev") || normalizedAction.equals("next") || normalizedAction.equals("page-next") || normalizedAction.equals("pageprevious") || normalizedAction.equals("pagenext")) {
                    if (normalizedAction.startsWith("page")) normalizedAction = normalizedAction.substring(4);
                    PersistentDataContainer data = itemMeta.getPersistentDataContainer();
                    data.set(
                            new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "pageAction"),
                            PersistentDataType.STRING,
                            normalizedAction.equals("prev") ? "previous" : normalizedAction
                    );
                    data.set(
                            new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "guiConfigPath"),
                            PersistentDataType.STRING,
                            configPath
                    );
                    data.set(
                            new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "guiPage"),
                            PersistentDataType.INTEGER,
                            gui.currentPage
                    );
                }
            }
            if (isDebug) {
                DuckAPI.getPlugin().getLogger().info("[DuckAPI] Marked item as GUI item in persistent data for " + configPath + "." + key);
            }

            itemMeta.setDisplayName(gui.replacePagePlaceholders(Message.convertStringToLegacy(itemSection.getString("Name", itemMeta.getDisplayName()))));
            itemMeta.setLore(gui.replacePagePlaceholders(Message.convertStringListToLegacy(itemSection.getStringList("Lore"))));
            item.setItemMeta(itemMeta);

            if (isDebug) {
                Bukkit.getLogger().info("[DuckAPI] Parsed GUI item at " + configPath + "." + key + " into slots " + Arrays.toString(itemSlots));
                Bukkit.getLogger().info("[DuckAPI] Item Material: " + item.getType());
                Bukkit.getLogger().info("[DuckAPI] Item Name: " + itemMeta.getDisplayName());
                Bukkit.getLogger().info("[DuckAPI] Item Lore: " + itemMeta.getLore());
                if (headUuid != null) {
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Player head name: " + Bukkit.getOfflinePlayer(headUuid));
                }
            }

            // Create a final copy of loop-local variables for use in lambdas
            final int[] fslots = itemSlots;
            final ItemStack fitem = item;
            final String fheadDbId = itemSection.getString("HeadDBId");

            // Build a CompletableFuture step that will do fetch/apply on the correct threads
            CompletableFuture<Void> step;

            if (DuckAPI.getEnableHeadAPIIntegration() && mat == Material.PLAYER_HEAD && fheadDbId != null && !fheadDbId.isEmpty()) {
                if (isDebug) {
                    if (headUuid != null)
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Setting player head for UUID: " + headUuid);
                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] Will fetch HeadDB ID: " + fheadDbId + " and wait for application before continuing");
                }

                // Async fetch, then apply on main thread and complete
                step = CompletableFuture.supplyAsync(() -> {
                    try {
                        if (isDebug) {
                            DuckAPI.getPlugin().getLogger().info("[DuckAPI] Obtaining HeadAPI and attempting to fetch HeadDB head for ID: " + fheadDbId);
                        }
                        HeadDatabaseAPI headAPI = DuckAPI.getHeadApiBlocking();
                        if (headAPI == null) {
                            if (isDebug)
                                DuckAPI.getPlugin().getLogger().info("[DuckAPI] HeadAPI is not available; skipping HeadDB lookup for ID: " + fheadDbId);
                            return null;
                        }
                        return headAPI.getItemHead(fheadDbId);
                    } catch (Exception e) {
                        if (isDebug)
                            DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Exception while fetching HeadDB head for ID " + fheadDbId + ": " + e.getMessage());
                        return null;
                    }
                }).thenCompose(headItem -> {
                    // Create a future that completes when the main-thread application is done
                    CompletableFuture<Void> applied = new CompletableFuture<>();

                    Bukkit.getScheduler().runTask(DuckAPI.getPlugin(), () -> {
                        try {
                            if (headItem == null) {
                                if (isDebug)
                                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] HeadDB did not return an item for ID: " + fheadDbId);
                                // still place the original item
                                placeItemAtSlots(gui, fitem, fslots);
                                applied.complete(null);
                                return;
                            }

                            if (!(headItem.getItemMeta() instanceof SkullMeta headMeta)) {
                                if (isDebug)
                                    DuckAPI.getPlugin().getLogger().info("[DuckAPI] HeadDB returned non-SkullMeta item for ID: " + fheadDbId);
                                placeItemAtSlots(gui, fitem, fslots);
                                applied.complete(null);
                                return;
                            }

                            // Use retrying applicator to ensure the owning/profile becomes available
                            applyHeadWithRetries(fitem, headMeta, fslots, gui, 5, isDebug, applied);
                        } catch (Exception ex) {
                            if (isDebug)
                                DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Error applying HeadDB item for ID " + fheadDbId + ": " + ex.getMessage());
                            applied.complete(null);
                        }
                    });

                    return applied;
                });
            } else {
                // No HeadDB processing required; just place the item on main thread and wait until done
                step = CompletableFuture.supplyAsync(() -> {
                    CompletableFuture<Void> placed = new CompletableFuture<>();
                    Bukkit.getScheduler().runTask(DuckAPI.getPlugin(), () -> {
                        try {
                            placeItemAtSlots(gui, fitem, fslots);
                        } catch (Exception ex) {
                            if (isDebug)
                                DuckAPI.getPlugin().getLogger().warning("[DuckAPI] Error placing GUI item at slots " + Arrays.toString(fslots) + ": " + ex.getMessage());
                        }
                        placed.complete(null);
                    });
                    try {
                        return placed.get();
                    } catch (Exception e) {
                        return null;
                    }
                }).thenApply(v -> null);
            }

            // Chain the step so we wait for each entry to finish before processing the next
            sequence = sequence.thenCompose(ignored -> step);
        }

        // Fill the configured pagination slots after every fixed item has been placed.
        return sequence.thenCompose(ignored -> {
            CompletableFuture<Void> filled = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(DuckAPI.getPlugin(), () -> {
                gui.applyPaginationItems();
                filled.complete(null);
            });
            return filled;
        }).thenApply(ignored -> gui);
    }

    // Try to apply the skull meta to the item, and wait and retry a few times if the profile/owner isn't present yet.
    private static void applyHeadWithRetries(ItemStack item, SkullMeta headMeta, int[] slots, GUI gui, int attemptsLeft, boolean isDebug, CompletableFuture<Void> done) {
        // run the attempt on the main thread
        Bukkit.getScheduler().runTask(DuckAPI.getPlugin(), () -> {
            try {
                ItemMeta currentMeta = item.getItemMeta();
                SkullMeta skullMeta = null;
                if (currentMeta instanceof SkullMeta) skullMeta = (SkullMeta) currentMeta;

                if (skullMeta != null && headMeta.getPlayerProfile() != null) {
                    skullMeta.setPlayerProfile(headMeta.getPlayerProfile());
                    item.setItemMeta(skullMeta);
                } else if (skullMeta != null && headMeta.getOwningPlayer() != null) {
                    skullMeta.setOwningPlayer(headMeta.getOwningPlayer());
                    item.setItemMeta(skullMeta);
                } else {
                    // fallback: copy the headMeta onto the item
                    ItemMeta preserved = currentMeta;
                    item.setItemMeta(headMeta);
                    ItemMeta newMeta = item.getItemMeta();
                    if (newMeta != null) {
                        try {
                            newMeta.setDisplayName(preserved.getDisplayName());
                        } catch (Exception ignored) {
                        }
                        try {
                            newMeta.setLore(preserved.getLore());
                        } catch (Exception ignored) {
                        }
                        try {
                            newMeta.getPersistentDataContainer().set(
                                    new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "isGuiItem"),
                                    PersistentDataType.BOOLEAN, true
                            );
                        } catch (Exception ignored) {
                        }
                        item.setItemMeta(newMeta);
                    }
                }

                // Verify if owner/profile is present now
                ItemMeta verifyMeta = item.getItemMeta();
                boolean applied = false;
                if (verifyMeta instanceof SkullMeta v) {
                    if (v.getPlayerProfile() != null) applied = true;
                    else if (v.getOwningPlayer() != null) {
                        try {
                            if (v.getOwningPlayer().getUniqueId() != null) applied = true;
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (applied || attemptsLeft <= 0) {
                    placeItemAtSlots(gui, item, slots);
                    if (isDebug)
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Applied HeadDB profile (or final fallback) and placed GUI slots " + Arrays.toString(slots) + " (applied=" + applied + ")");
                    done.complete(null);
                } else {
                    // schedule another attempt in a few ticks
                    if (isDebug)
                        DuckAPI.getPlugin().getLogger().info("[DuckAPI] Head not applied yet; retrying for slots " + Arrays.toString(slots) + " attemptsLeft=" + attemptsLeft);
                    Bukkit.getScheduler().runTaskLater(DuckAPI.getPlugin(), () -> applyHeadWithRetries(item, headMeta, slots, gui, attemptsLeft - 1, isDebug, done), 2L);
                }
            } catch (Exception e) {
                if (isDebug)
                    DuckAPI.getPlugin().getLogger().warning("[DuckAPI] applyHeadWithRetries error: " + e.getMessage());
                try {
                    placeItemAtSlots(gui, item, slots);
                } catch (Exception ignored) {
                }
                done.complete(null);
            }
        });
    }

    private static LinkedHashMap<String, ConfigurationSection> findRenderableItemSections(ConfigurationSection section) {
        LinkedHashMap<String, ConfigurationSection> items = new LinkedHashMap<>();

        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;

            if (key.equalsIgnoreCase("Pagination") || key.equalsIgnoreCase("paginationSettings")) {
                for (String nestedKey : item.getKeys(false)) {
                    ConfigurationSection nestedItem = item.getConfigurationSection(nestedKey);
                    if (nestedItem != null) {
                        items.put("Pagination." + nestedKey, nestedItem);
                    }
                }
            } else {
                items.put(key, item);
            }
        }

        return items;
    }

    private static int[] findItemSlots(ConfigurationSection item) {
        List<Integer> slots = item.getIntegerList("Slots");
        if (!slots.isEmpty()) return toSlotArray(slots);
        if (item.contains("Slot")) return new int[]{item.getInt("Slot")};
        return new int[]{0};
    }

    private static void placeItemAtSlots(GUI gui, ItemStack item, int[] slots) {
        for (int slot : slots) {
            if (slot >= 0 && slot < gui.getSize()) {
                gui.setSlot(slot, item);
            }
        }
    }

    private static int[] findPaginationSlots(String configPath, ConfigurationSection section) {
        Configuration config = DuckAPI.getPlugin().getConfig();
        String[] names = {"PaginationSlots", "FillSlots", "ContentSlots", "ItemSlots", "Slots"};
        for (String name : names) {
            List<Integer> slots = config.getIntegerList(configPath + "." + name);
            if (!slots.isEmpty()) return toSlotArray(slots);
        }

        ConfigurationSection pagination = config.getConfigurationSection(configPath + ".Pagination");
        if (pagination != null) {
            List<Integer> slots = pagination.getIntegerList("Slots");
            if (slots.isEmpty()) slots = pagination.getIntegerList("Slot");
            if (!slots.isEmpty()) return toSlotArray(slots);
        }

        ArrayList<Integer> found = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;
            if (item.contains("Slot") && (!item.contains("Material") || item.getString("Material", "AIR").equalsIgnoreCase("AIR"))) {
                found.add(item.getInt("Slot"));
            }
        }
        if (!found.isEmpty()) {
            Collections.sort(found);
            return toSlotArray(found);
        }
        return new int[0];
    }

    private int normalizePage(int page) {
        if (pageCount <= 1) return 0;
        if (page < 0) {
            return infinitePagination ? (pageCount - ((-page) % pageCount)) % pageCount : 0;
        }
        if (page >= pageCount) {
            return infinitePagination ? page % pageCount : pageCount - 1;
        }
        return page;
    }

    private String replacePagePlaceholders(@Nullable String text) {
        if (text == null) return null;
        return text
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%page_amount%", String.valueOf(pageCount));
    }

    private List<String> replacePagePlaceholders(@Nullable List<String> lore) {
        if (lore == null) return null;
        List<String> replaced = new ArrayList<>(lore.size());
        for (String line : lore) {
            replaced.add(replacePagePlaceholders(line));
        }
        return replaced;
    }

    private void replacePagePlaceholders(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (meta.hasDisplayName()) {
            meta.setDisplayName(replacePagePlaceholders(meta.getDisplayName()));
        }
        if (meta.hasLore()) {
            meta.setLore(replacePagePlaceholders(meta.getLore()));
        }
        item.setItemMeta(meta);
    }

    private boolean isPageActionVisible(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return true;
        String action = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "pageAction"),
                PersistentDataType.STRING
        );
        if ("previous".equals(action) || "prev".equals(action)) return hasPreviousPage();
        if ("next".equals(action)) return hasNextPage();
        return true;
    }

    public void applyPaginationItems() {
        synchronized (OPEN_GUIS) {
            OPEN_GUIS.put(inventory, this);
        }
        for (int slot : paginatedSlots) {
            if (slot >= 0 && slot < inventory.getSize() && !fixedSlots.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }
        int offset = currentPage * pageSize;
        for (int i = 0; i < paginatedSlots.length && offset + i < paginatedItems.length; i++) {
            int slot = paginatedSlots[i];
            if (fixedSlots.containsKey(slot)) continue;
            ItemStack item = paginatedItems[offset + i];
            if (item != null) {
                ItemStack clonedItem = item.clone();
                replacePagePlaceholders(clonedItem);
                setSlot(slot, clonedItem);
            }
        }

        for (Map.Entry<Integer, ItemStack> fixed : fixedSlots.entrySet()) {
            ItemStack fixedItem = fixed.getValue().clone();
            replacePagePlaceholders(fixedItem);
            if (isPageActionVisible(fixedItem)) {
                inventory.setItem(fixed.getKey(), fixedItem);
            } else {
                inventory.setItem(fixed.getKey(), null);
            }
        }

        NamespacedKey pageKey = new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "guiPage");
        NamespacedKey configKey = new NamespacedKey(DuckAPI.getPlugin(), DuckAPI.getPersistentDataKey() + "guiConfigPath");
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getItemMeta() == null) continue;
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            if (data.has(pageKey, PersistentDataType.INTEGER) || data.has(configKey, PersistentDataType.STRING)) {
                data.set(pageKey, PersistentDataType.INTEGER, currentPage);
                data.set(configKey, PersistentDataType.STRING, configPath);
                item.setItemMeta(meta);
            }
        }
    }

    public CompletableFuture<GUI> nextPage() {
        if (paginatedItems.length == 0 || paginatedSlots.length == 0) return CompletableFuture.completedFuture(this);
        if (!hasNextPage()) return CompletableFuture.completedFuture(this);
        currentPage = infinitePagination
                ? (currentPage + 1) % pageCount
                : Math.min(currentPage + 1, pageCount - 1);
        applyPaginationItems();
        return CompletableFuture.completedFuture(this);
    }

    public CompletableFuture<GUI> previousPage() {
        if (paginatedItems.length == 0 || paginatedSlots.length == 0) return CompletableFuture.completedFuture(this);
        if (!hasPreviousPage()) return CompletableFuture.completedFuture(this);
        currentPage = infinitePagination
                ? (currentPage - 1 + pageCount) % pageCount
                : Math.max(currentPage - 1, 0);
        applyPaginationItems();
        return CompletableFuture.completedFuture(this);
    }

    public boolean hasNextPage() {
        return pageCount > 1 && (infinitePagination || currentPage + 1 < pageCount);
    }

    public boolean hasPreviousPage() {
        return pageCount > 1 && (infinitePagination || currentPage > 0);
    }

    public void setSlot(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        boolean paginationSlot = false;
        for (int paginatedSlot : paginatedSlots) {
            if (paginatedSlot == slot) {
                paginationSlot = true;
                break;
            }
        }
        if (item != null && !paginationSlot) {
            fixedSlots.put(slot, item.clone());
        }
        inventory.setItem(slot, item);
    }

    public ItemStack getSlot(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) return null;
        return inventory.getItem(slot);
    }
}
