package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class SpawnerItemFactory {

    private final SpawnerKeys keys;

    public SpawnerItemFactory(SpawnerKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(EntityType mobType, int level) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();

        String mobName = NameUtil.prettify(mobType);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&7[Lv." + level + "] &f" + mobName + " Spawner"));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&8Passively generates loot and XP."),
                ChatColor.translateAlternateColorCodes('&', "&8Place, or stack onto a matching spawner to level it up.")
        ));

        meta.getPersistentDataContainer().set(keys.itemMobType, PersistentDataType.STRING, mobType.name());
        meta.getPersistentDataContainer().set(keys.itemLevel, PersistentDataType.INTEGER, level);

        // Suppresses vanilla's own auto-generated tooltip additions for this
        // item type (e.g. "Interact with Spawn Egg: Sets Mob Type") - that
        // hint is baked into the base SPAWNER item type by the game itself
        // and shows up regardless of our custom lore, since it's a separate
        // tooltip component from the lore we set above.
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        item.setItemMeta(meta);
        return item;
    }

    /** Reads the mob type off a spawner ITEM (not a placed block) - null if this isn't one of ours. */
    public EntityType readMobType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(keys.itemMobType, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return EntityType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int readLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(keys.itemLevel, PersistentDataType.INTEGER, 1);
    }
}
