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

    public static final int MOBS_PER_PAGE = 45; // rows 1-5, leaving row 6 for navigation
    public static final int PREV_PAGE_SLOT = 45;
    public static final int PAGE_INDICATOR_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 53;

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

    private List<EntityType> enabledMobs() {
        return config.registry().entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .sorted() // enum natural order (declaration order) - stable and predictable across pages
                .toList();
    }

    public int pageCount() {
        int total = enabledMobs().size();
        return Math.max(1, (int) Math.ceil(total / (double) MOBS_PER_PAGE));
    }

    public Inventory build(int page) {
        List<EntityType> enabledMobs = enabledMobs();
        int totalPages = pageCount();
        page = Math.max(0, Math.min(page, totalPages - 1));

        SpawnerGuiHolder holder = new SpawnerGuiHolder();
        holder.page = page;
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, 54,
                config.message("gui-title") + ChatColor.DARK_GRAY + " (" + (page + 1) + "/" + totalPages + ")");
        holder.setInventory(inventory);

        int start = page * MOBS_PER_PAGE;
        int end = Math.min(start + MOBS_PER_PAGE, enabledMobs.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, buildIcon(enabledMobs.get(i)));
        }

        buildNavigationRow(inventory, page, totalPages);

        return inventory;
    }

    private void buildNavigationRow(Inventory inventory, int page, int totalPages) {
        ItemStack filler = filler();
        for (int i = PREV_PAGE_SLOT; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lPrevious Page"));
            prev.setItemMeta(meta);
            inventory.setItem(PREV_PAGE_SLOT, prev);
        }

        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta indicatorMeta = indicator.getItemMeta();
        indicatorMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&7Page " + (page + 1) + " of " + totalPages));
        indicator.setItemMeta(indicatorMeta);
        inventory.setItem(PAGE_INDICATOR_SLOT, indicator);

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lNext Page"));
            next.setItemMeta(meta);
            inventory.setItem(NEXT_PAGE_SLOT, next);
        }
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

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
