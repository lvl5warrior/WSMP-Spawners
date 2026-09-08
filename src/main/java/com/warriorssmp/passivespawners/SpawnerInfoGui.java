package com.warriorssmp.passivespawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SpawnerInfoGui {

    public static final int STORAGE_SLOTS_START = 9;
    public static final int STORAGE_SLOTS_COUNT = 45; // rows 1-5 of a 54-slot inventory
    public static final int XP_BUTTON_SLOT = 4;
    public static final int ITEMS_BUTTON_SLOT = 8;
    public static final int INFO_ICON_SLOT = 0;

    private final ConfigManager config;
    private final SpawnerManager manager;
    private final SpawnerItemFactory itemFactory;

    public SpawnerInfoGui(ConfigManager config, SpawnerManager manager, SpawnerItemFactory itemFactory) {
        this.config = config;
        this.manager = manager;
        this.itemFactory = itemFactory;
    }

    public Inventory build(Location location, SpawnerData data) {
        SpawnerInfoGuiHolder holder = new SpawnerInfoGuiHolder(location, data);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                config.message("info-gui-title", "%mob%", NameUtil.prettify(data.mobType)));
        holder.setInventory(inventory);

        ItemStack filler = filler();
        for (int i = 1; i < STORAGE_SLOTS_START; i++) {
            if (i != XP_BUTTON_SLOT && i != ITEMS_BUTTON_SLOT) {
                inventory.setItem(i, filler);
            }
        }

        inventory.setItem(INFO_ICON_SLOT, buildInfoIcon(location, data));
        inventory.setItem(XP_BUTTON_SLOT, buildXpButton(data));
        inventory.setItem(ITEMS_BUTTON_SLOT, buildItemsButton(data));

        for (int i = 0; i < data.storedItems.size() && i < STORAGE_SLOTS_COUNT; i++) {
            inventory.setItem(STORAGE_SLOTS_START + i, data.storedItems.get(i));
        }

        // A snapshot of what was shown, taken at open time - needed later so
        // we can compute exactly what the player took rather than treating
        // "whatever's in the GUI's slots at close time" as the whole truth.
        // The live data.storedItems list can keep growing from generation
        // cycles the whole time this GUI is open; replacing it outright with
        // the GUI's contents at close would silently discard anything added
        // in the meantime.
        holder.openedSnapshot = data.storedItems.stream().map(ItemStack::clone).toList();

        return inventory;
    }

    public ItemStack buildInfoIcon(Location location, SpawnerData data) {
        ItemStack icon = itemFactory.create(data.mobType, data.level);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&lSpawner Info"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Mob: " + ChatColor.WHITE + NameUtil.prettify(data.mobType));
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + data.level + ChatColor.GRAY + " / " + config.maxSpawnerLevel());
        lore.add(ChatColor.GRAY + "Stored XP: " + ChatColor.WHITE + String.format("%.1f", data.storedXp));

        ConfigManager.MobConfig mobConfig = config.get(data.mobType);
        if (mobConfig != null) {
            double xpPerCycle = mobConfig.baseXpReward() * (1.0 + (data.level - 1) * config.xpMultiplierPerLevel());
            int lootRolls = Math.max(1, (int) Math.round(1.0 + (data.level - 1) * config.lootMultiplierPerLevel()));
            int effectiveInterval = Math.max(1, mobConfig.generationIntervalTicks()
                    - (data.level - 1) * config.intervalReductionPerLevelTicks());
            long ticksElapsed = manager.currentTick() - data.lastGenerationTick;
            long ticksRemaining = Math.max(0, effectiveInterval - ticksElapsed);

            lore.add("");
            lore.add(ChatColor.GRAY + "XP per cycle: " + ChatColor.WHITE + String.format("%.1f", xpPerCycle));
            lore.add(ChatColor.GRAY + "Loot rolls per cycle: " + ChatColor.WHITE + lootRolls);
            lore.add(ChatColor.GRAY + "Generation interval: " + ChatColor.WHITE + effectiveInterval
                    + ChatColor.GRAY + " ticks (" + String.format("%.1f", effectiveInterval / 20.0) + "s)");
            lore.add(ChatColor.GRAY + "Next generation in: " + ChatColor.WHITE
                    + String.format("%.1f", ticksRemaining / 20.0) + "s");
        } else {
            lore.add("");
            lore.add(ChatColor.RED + "This mob type is currently disabled in config.yml!");
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Stored items: " + ChatColor.WHITE + data.storedItems.size()
                + ChatColor.GRAY + " / " + config.maxStoredItemStacks() + " stacks");
        lore.add(ChatColor.GRAY + "Break behavior: " + ChatColor.WHITE + config.breakBehavior());
        lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + location.getWorld().getName() + " "
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());

        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    public ItemStack buildXpButton(SpawnerData data) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lGrab All XP"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Stored: " + ChatColor.WHITE + String.format("%.1f", data.storedXp) + " XP",
                ChatColor.YELLOW + "Click to claim!"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildItemsButton(SpawnerData data) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&lGrab All Items"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Stored: " + ChatColor.WHITE + data.storedItems.size() + ChatColor.GRAY + " stack(s)",
                ChatColor.YELLOW + "Click to collect everything!"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
