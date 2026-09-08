package com.warriorssmp.passivespawners;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rolls a mob's OFFICIAL vanilla loot table directly, the number of times
 * dictated by the spawner's level, and merges the results into stacks -
 * this is the core of the "no real entity ever spawns" design: we never
 * touch the entity spawning pipeline at all, just the loot table it would
 * have used if it existed.
 */
public class LootGenerator {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Random random = new Random();

    public LootGenerator(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Resolves the vanilla LootTable for a given mob type, if one exists. */
    private LootTable resolveLootTable(EntityType type) {
        try {
            LootTables entry = LootTables.valueOf(type.name());
            return entry.getLootTable();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("No vanilla loot table found for entity type " + type
                    + " - check spawner-registry in config.yml.");
            return null;
        }
    }

    public List<ItemStack> generateLoot(EntityType type, int level, Location location) {
        LootTable table = resolveLootTable(type);
        if (table == null) return List.of();

        double multiplier = 1.0 + (level - 1) * config.lootMultiplierPerLevel();
        int rolls = Math.max(1, (int) Math.round(multiplier));

        LootContext context = new LootContext.Builder(location).build();

        List<ItemStack> combined = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            for (ItemStack drop : table.populateLoot(random, context)) {
                mergeInto(combined, drop);
            }
        }
        return combined;
    }

    public double generateXp(EntityType type, int level) {
        ConfigManager.MobConfig mobConfig = config.get(type);
        double base = mobConfig != null ? mobConfig.baseXpReward() : 0.0;
        double multiplier = 1.0 + (level - 1) * config.xpMultiplierPerLevel();
        return base * multiplier;
    }

    /**
     * Adds a drop into the running list, combining it into an existing
     * similar stack where possible rather than always appending a new one -
     * keeps output tidy instead of e.g. ten separate "1x Rotten Flesh" stacks.
     */
    private void mergeInto(List<ItemStack> combined, ItemStack drop) {
        int remaining = drop.getAmount();
        int maxStack = drop.getMaxStackSize();

        for (ItemStack existing : combined) {
            if (remaining <= 0) break;
            if (!existing.isSimilar(drop)) continue;
            int space = maxStack - existing.getAmount();
            if (space <= 0) continue;
            int add = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
        }

        while (remaining > 0) {
            int amount = Math.min(maxStack, remaining);
            ItemStack newStack = drop.clone();
            newStack.setAmount(amount);
            combined.add(newStack);
            remaining -= amount;
        }
    }
}
