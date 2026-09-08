package com.warriorssmp.passivespawners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PassiveSpawnersPlugin extends JavaPlugin {

    private SpawnerKeys keys;
    private ConfigManager config;
    private SpawnerManager manager;
    private LootGenerator lootGenerator;
    private LootRouter lootRouter;
    private HologramManager holograms;
    private SpawnerItemFactory itemFactory;

    @Override
    public void onEnable() {
        keys = new SpawnerKeys(this);
        config = new ConfigManager(this);
        config.load();

        manager = new SpawnerManager(this, keys, config);
        lootGenerator = new LootGenerator(this, config);
        lootRouter = new LootRouter();
        holograms = new HologramManager(this, config, keys);
        itemFactory = new SpawnerItemFactory(keys);
        SpawnerGui gui = new SpawnerGui(this, config, itemFactory);
        SpawnerInfoGui infoGui = new SpawnerInfoGui(config, manager, itemFactory);

        getServer().getPluginManager().registerEvents(
                new SpawnerPlaceListener(manager, itemFactory, holograms, config), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerBreakListener(manager, itemFactory, holograms, config), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerInteractListener(manager, infoGui, config), this);
        getServer().getPluginManager().registerEvents(
                new VanillaSpawnBlocker(manager), this);
        getServer().getPluginManager().registerEvents(
                new ChunkTrackingListener(manager, holograms), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerExplosionListener(manager, holograms), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerGuiListener(this, itemFactory, config, gui), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerInfoGuiListener(manager, infoGui, config), this);
        getServer().getPluginManager().registerEvents(holograms, this);

        getCommand("wsmpadmin").setExecutor(new SpawnerAdminCommand(manager, itemFactory, gui));

        // The core generation loop - runs every tick; SpawnerManager.tick()
        // itself only actually acts on a spawner once its own interval has
        // elapsed, so this isn't doing real work for most spawners most ticks.
        getServer().getScheduler().runTaskTimer(this, () -> manager.tick(this::onGenerate), 0L, 1L);

        // Separate, less-frequent loop for hologram line-of-sight - a
        // player's view of a hologram changes constantly as they move, so
        // this needs its own ongoing check, not just a one-time decision
        // made when the hologram was first created. Player-centric (not
        // hologram-centric): for each player, find their single closest
        // valid spawner and show only that one - see HologramManager's
        // updateVisibilityForPlayer for why.
        long visibilityInterval = config.hologramVisibilityCheckIntervalTicks();
        getServer().getScheduler().runTaskTimer(this, () -> {
            var allData = manager.allTracked().values();
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                holograms.updateVisibilityForPlayer(player, allData);
            }
        }, visibilityInterval, visibilityInterval);

        getLogger().info("WSMP-PassiveSpawners enabled.");
    }

    private void onGenerate(Location location, SpawnerData data) {
        Block block = location.getBlock();
        if (!manager.isTrackedSpawnerBlock(block)) {
            // The block changed out from under us without going through our
            // own break listener (an explosion, another plugin, etc.) - stop
            // tracking it rather than keep generating loot into thin air.
            manager.unregister(location);
            if (data.hologram != null) holograms.remove(data.hologram);
            return;
        }

        // Loot and XP are handled in fully independent try/catch blocks -
        // deliberately, after finding that a single shared try block meant
        // one mob's loot table throwing (see LootGenerator's own comment on
        // this) silently killed XP generation too, for every mob, every
        // cycle. Neither should ever be collateral damage from the other
        // failing.
        try {
            List<ItemStack> loot = lootGenerator.generateLoot(data.mobType, data.level, location);
            List<ItemStack> unrouted = lootRouter.route(block, loot);

            if (!unrouted.isEmpty()) {
                List<ItemStack> overflow = mergeIntoStorage(data.storedItems, unrouted, config.maxStoredItemStacks());
                if (!overflow.isEmpty()) {
                    // Internal storage is also full (not just any attached
                    // container) - this is the true last resort, not the
                    // normal path, so nothing is ever silently discarded.
                    Location dropAt = location.clone().add(0.5, 1.0, 0.5);
                    for (ItemStack item : overflow) {
                        dropAt.getWorld().dropItemNaturally(dropAt, item);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("[WSMP-PassiveSpawners] Loot generation/routing failed for a " + data.mobType
                    + " spawner at " + location + " (level " + data.level + "): " + e);
        }

        try {
            double xpGained = lootGenerator.generateXp(data.mobType, data.level);
            data.storedXp += xpGained;
        } catch (Exception e) {
            getLogger().warning("[WSMP-PassiveSpawners] XP generation failed for a " + data.mobType
                    + " spawner at " + location + " (level " + data.level + "): " + e);
        }

        try {
            manager.writeToBlock(block, data);
        } catch (Exception e) {
            getLogger().warning("[WSMP-PassiveSpawners] Failed to persist spawner state at " + location + ": " + e);
        }
    }

    /**
     * Merges newItems into the storage list, combining into existing similar
     * stacks first, then creating new stacks up to maxStacks. Anything that
     * still doesn't fit once the stack-count cap is reached is returned as
     * true overflow for the caller to handle (currently: drop on the ground).
     */
    private List<ItemStack> mergeIntoStorage(List<ItemStack> storage, List<ItemStack> newItems, int maxStacks) {
        List<ItemStack> overflow = new java.util.ArrayList<>();
        for (ItemStack item : newItems) {
            int remaining = item.getAmount();
            int maxStackSize = item.getMaxStackSize();

            for (ItemStack existing : storage) {
                if (remaining <= 0) break;
                if (!existing.isSimilar(item)) continue;
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) continue;
                int add = Math.min(space, remaining);
                existing.setAmount(existing.getAmount() + add);
                remaining -= add;
            }

            while (remaining > 0) {
                if (storage.size() >= maxStacks) {
                    ItemStack over = item.clone();
                    over.setAmount(remaining);
                    overflow.add(over);
                    break;
                }
                int amount = Math.min(maxStackSize, remaining);
                ItemStack newStack = item.clone();
                newStack.setAmount(amount);
                storage.add(newStack);
                remaining -= amount;
            }
        }
        return overflow;
    }
}
