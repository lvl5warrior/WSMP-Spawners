package com.warriorssmp.passivespawners;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * All PDC keys in one place so item-tagging and block-tagging code can never
 * drift out of sync with each other.
 */
public class SpawnerKeys {

    public final NamespacedKey blockMobType;
    public final NamespacedKey blockLevel;
    public final NamespacedKey blockStoredXp;
    public final NamespacedKey blockLastGenerationTick;

    // Separate keys for the un-placed ITEM, so an item sitting in an
    // inventory can't accidentally be confused with block state.
    public final NamespacedKey itemMobType;
    public final NamespacedKey itemLevel;

    public SpawnerKeys(Plugin plugin) {
        blockMobType = new NamespacedKey(plugin, "spawner_mob_type");
        blockLevel = new NamespacedKey(plugin, "spawner_level");
        blockStoredXp = new NamespacedKey(plugin, "spawner_stored_xp");
        blockLastGenerationTick = new NamespacedKey(plugin, "spawner_last_generation_tick");

        itemMobType = new NamespacedKey(plugin, "spawner_item_mob_type");
        itemLevel = new NamespacedKey(plugin, "spawner_item_level");
    }
}
