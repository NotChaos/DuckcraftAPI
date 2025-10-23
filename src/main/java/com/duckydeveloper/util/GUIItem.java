package com.duckydeveloper.util;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class GUIItem {
    @Getter
    protected final String name;
    @Getter
    protected final @NotNull String[] lore;
    @Getter
    protected final int slot;
    @Getter
    protected final Material material;
    protected final Plugin plugin;

    public GUIItem(Plugin plugin, String name, @NotNull String[] lore, int slot, Material material) {
        this.plugin = plugin;
        this.name = name;
        this.lore = lore;
        this.slot = slot;
        this.material = material;
    }
}
