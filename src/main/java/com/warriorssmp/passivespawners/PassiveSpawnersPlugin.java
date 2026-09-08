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
        holograms = new HologramManager(this, config);
        itemFactory = new SpawnerItemFactory(keys);

        getServer().getPluginManager().registerEvents(
                new SpawnerPlaceListener(manager, itemFactory, holograms, config), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerBreakListener(manager, itemFactory, holograms, config), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerInteractListener(manager, config), this);
        getServer().getPluginManager().registerEvents(
                new VanillaSpawnBlocker(manager), this);
        getServer().getPluginManager().registerEvents(
                new ChunkTrackingListener(manager, holograms), this);
        getServer().getPluginManager().registerEvents(
                new SpawnerExplosionListener(manager, holograms), this);

        getCommand("wsmpadmin").setExecutor(new SpawnerAdminCommand(manager, itemFactory));

        // The core generation loop - runs every tick; SpawnerManager.tick()
        // itself only actually acts on a spawner once its own interval has
        // elapsed, so this isn't doing real work for most spawners most ticks.
        getServer().getScheduler().runTaskTimer(this, () -> manager.tick(this::onGenerate), 0L, 1L);

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

        // This runs off a schedule, not a direct player action - there is no
        // player to send a chat message to. The equivalent of "surface the
        // error instead of failing silently" here is a clear, specific
        // console warning (not just a bare stack trace) rather than letting
        // an exception here silently skip a generation cycle with no trace
        // of why.
        try {
            List<ItemStack> loot = lootGenerator.generateLoot(data.mobType, data.level, location);
            lootRouter.route(block, loot);

            double xpGained = lootGenerator.generateXp(data.mobType, data.level);
            data.storedXp += xpGained;

            manager.writeToBlock(block, data);
        } catch (Exception e) {
            getLogger().warning("[WSMP-PassiveSpawners] Generation failed for a " + data.mobType
                    + " spawner at " + location + " (level " + data.level + "): " + e);
        }
    }
}
