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
 * dictated by the spawner's level, and merges the results into stacks.
 *
 * IMPORTANT - this is NOT "zero entity, ever" anymore. Most vanilla loot
 * tables need real entity/damage-source context (looting-enchantment
 * bonuses, kill-condition checks, size-based scaling) that a bare
 * Location-only LootContext can't supply - confirmed by testing, not
 * assumed (Slime and Creeper both threw the identical missing-parameter
 * exception, so this is systemic across most mobs, not a one-off). To get
 * real loot at all, this briefly spawns a fully inert, invisible,
 * invulnerable, AI-less phantom entity purely to satisfy that context, then
 * removes it in the same synchronous instant before it can ever be
 * rendered or ticked. The spawner block itself still never spawns a real,
 * persistent, AI-driven mob - that part of the original design holds - but
 * loot generation specifically now makes real, if momentary, use of the
 * entity spawning pipeline.
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

    // Never attempted for these regardless of config - Wither, Ender Dragon,
    // and Warden are tied to special internal systems (boss bars, dragon-fight
    // state tracking, unique sensing/AI) that don't cleanly correspond to
    // "spawn it, immediately remove it." Even with instant removal, briefly
    // spawning one of these could trigger side effects that don't
    // necessarily undo just because the entity itself is gone. Bosses are
    // already disabled by default in config.yml, but that's a config choice
    // an admin could change - this is the code itself refusing to risk it
    // regardless of what config says.
    private static final java.util.Set<EntityType> NEVER_PHANTOM_SPAWN = java.util.Set.of(
            EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN
    );

    public List<ItemStack> generateLoot(EntityType type, int level, Location location) {
        LootTable table = resolveLootTable(type);
        if (table == null) return List.of();

        double multiplier = 1.0 + (level - 1) * config.lootMultiplierPerLevel();
        int rolls = Math.max(1, (int) Math.round(multiplier));

        List<ItemStack> combined = new ArrayList<>();

        // Most vanilla loot tables (looting-enchantment bonuses, kill-condition
        // checks like Creeper's music disc, size-based scaling like Slime)
        // need real entity/damage-source context that a bare Location-only
        // LootContext can't supply - confirmed directly by testing (Slime AND
        // Creeper both threw the identical "missing this_entity/damage_source"
        // exception, so this is systemic, not a one-mob edge case). Rather
        // than hand-write custom drop tables for every mob (much more work,
        // much easier to get wrong, and drifts from real vanilla drops), this
        // spawns a genuinely real but fully inert, ultra-short-lived entity
        // purely to satisfy that context, then removes it in the same
        // synchronous instant - before it can ever be rendered, ticked, or
        // seen by any client. This is a real, if brief, use of the entity
        // spawning pipeline - a deliberate tradeoff, not the original "zero
        // entity, ever" design this plugin started with.
        org.bukkit.entity.Entity phantom = null;
        if (!NEVER_PHANTOM_SPAWN.contains(type)) {
            try {
                Class<? extends org.bukkit.entity.Entity> entityClass = type.getEntityClass();
                if (entityClass != null) {
                    phantom = location.getWorld().spawn(location, entityClass);
                    phantom.setInvulnerable(true);
                    phantom.setSilent(true);
                    phantom.setGravity(false);
                    if (phantom instanceof org.bukkit.entity.LivingEntity living) {
                        living.setAI(false);
                        living.setInvisible(true);
                        living.setCollidable(false);
                    }
                }
            } catch (Exception e) {
                // Some entity types can't be spawned this generically (or another
                // plugin's spawn-limiting/anti-grief logic blocked it) - fall
                // through with phantom still null, same as if this technique
                // didn't exist at all.
                phantom = null;
            }
        }

        LootContext.Builder contextBuilder = new LootContext.Builder(location);
        if (phantom != null) {
            contextBuilder.lootedEntity(phantom);
        }
        LootContext context = contextBuilder.build();

        try {
            for (int i = 0; i < rolls; i++) {
                for (ItemStack drop : table.populateLoot(random, context)) {
                    mergeInto(combined, drop);
                }
            }
        } catch (IllegalArgumentException e) {
            // Even with a real entity for context, some loot table function
            // needed something this still doesn't provide (a specific damage
            // type, a killer, etc). Degrade gracefully - XP still generates
            // normally regardless, this mob just won't produce items this
            // cycle.
            logIncompatibleLootTableOnce(type, e);
        } finally {
            if (phantom != null) {
                phantom.remove();
            }
        }

        return combined;
    }

    private final java.util.Set<EntityType> warnedIncompatible = new java.util.HashSet<>();

    private void logIncompatibleLootTableOnce(EntityType type, Exception e) {
        if (warnedIncompatible.add(type)) {
            plugin.getLogger().warning("[WSMP-PassiveSpawners] " + type + "'s vanilla loot table needs entity/damage "
                    + "context beyond what even a real (temporary) entity provided - it will still generate XP "
                    + "normally, but won't produce item drops. (" + e.getMessage() + ")");
        }
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
