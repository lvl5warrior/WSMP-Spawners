package com.warriorssmp.passivespawners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;

/**
 * The underlying block is a real vanilla CreatureSpawner (kept for its
 * convenient PersistentDataContainer support and familiar visual/model) -
 * but this plugin's whole point is that it should NEVER actually spawn a
 * real entity. Vanilla's own internal spawn timer still runs on the block
 * regardless of anything else we do, so this is what actually neutralizes it.
 */
public class VanillaSpawnBlocker implements Listener {

    private final SpawnerManager manager;

    public VanillaSpawnBlocker(SpawnerManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (manager.isTrackedSpawnerBlock(event.getSpawner().getBlock())) {
            event.setCancelled(true);
        }
    }
}
