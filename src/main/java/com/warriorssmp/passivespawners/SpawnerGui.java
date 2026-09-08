package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class SpawnerGui {

    private static final int MAX_SLOTS = 54; // one double chest - see the log warning below if exceeded

    private final ConfigManager config;
    private final SpawnerItemFactory itemFactory;
    private final JavaPlugin plugin;

    public SpawnerGui(JavaPlugin plugin, ConfigManager config, SpawnerItemFactory itemFactory) {
        this.plugin = plugin;
        this.config = config;
        this.itemFactory = itemFactory;
    }

    public boolean hasAnyEnabledMobs() {
        return config.registry().values().stream().anyMatch(ConfigManager.MobConfig::enabled);
    }

    public Inventory build() {
        Map<EntityType, ConfigManager.MobConfig> registry = config.registry();
        List<EntityType> enabledMobs = registry.entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        int slots = Math.max(9, (int) (Math.ceil(enabledMobs.size() / 9.0) * 9));
        if (slots > MAX_SLOTS) {
            plugin.getLogger().warning("[WSMP-PassiveSpawners] " + enabledMobs.size()
                    + " mob types are enabled in spawner-registry, more than fit in one GUI page ("
                    + MAX_SLOTS + " slots) - only the first " + MAX_SLOTS + " will be shown. "
                    + "Consider disabling unused mob types in config.yml.");
            slots = MAX_SLOTS;
        }

        SpawnerGuiHolder holder = new SpawnerGuiHolder();
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, slots, config.message("gui-title"));
        holder.setInventory(inventory);

        int slot = 0;
        for (EntityType mobType : enabledMobs) {
            if (slot >= MAX_SLOTS) break;
            inventory.setItem(slot, buildIcon(mobType));
            slot++;
        }

        return inventory;
    }

    private ItemStack buildIcon(EntityType mobType) {
        // The icon itself is a real spawner item (level 1) - not just a
        // decorative display - so the click listener can read its mob type
        // straight back off the clicked ItemStack via SpawnerItemFactory,
        // with no separate slot-to-mob mapping to keep in sync.
        ItemStack icon = itemFactory.create(mobType, 1);

        Material eggMaterial = resolveSpawnEgg(mobType);
        if (eggMaterial != null) {
            icon.setType(eggMaterial);
        }
        // If no spawn egg exists for this mob (bosses, etc.), the icon just
        // stays as the normal Material.SPAWNER item - still perfectly clear
        // given its display name already says which mob it is.

        ItemMeta meta = icon.getItemMeta();
        List<String> lore = new java.util.ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        lore.add(ChatColor.translateAlternateColorCodes('&', "&e&lClick&8: &7give 1 to yourself"));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&e&lShift-Click&8: &7give a full stack (64)"));
        meta.setLore(lore);
        icon.setItemMeta(meta);

        return icon;
    }

    private Material resolveSpawnEgg(EntityType mobType) {
        try {
            return Material.valueOf(mobType.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return null; // no spawn egg for this mob (e.g. bosses) - fall back to the spawner icon itself
        }
    }
}
