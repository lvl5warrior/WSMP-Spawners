package com.warriorssmp.passivespawners;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

/**
 * BlockBreakEvent (handled in SpawnerBreakListener) only fires for direct
 * player breaks. An explosion destroys blocks through entirely different
 * events, which never touched our cleanup logic at all before this listener
 * existed - meaning a spawner destroyed by TNT or a creeper would leave its
 * hologram orphaned in the world and its tracking entry stale in memory
 * until the next scheduled generation attempt happened to notice the block
 * was gone (which could be a long wait for a high-level, long-interval
 * spawner). This listener closes that gap directly.
 *
 * Deliberately does NOT drop an item here, matching how vanilla spawner
 * blocks already don't drop themselves from an explosion - only cleanup
 * happens; there is no "break-behavior" applied for explosions.
 */
public class SpawnerExplosionListener implements Listener {

    private final SpawnerManager manager;
    private final HologramManager holograms;

    public SpawnerExplosionListener(SpawnerManager manager, HologramManager holograms) {
        this.manager = manager;
        this.holograms = holograms;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            cleanupIfTracked(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            cleanupIfTracked(block);
        }
    }

    private void cleanupIfTracked(Block block) {
        if (!manager.isTrackedSpawnerBlock(block)) return;
        SpawnerData data = manager.unregister(block.getLocation());
        if (data != null && data.hologram != null) {
            holograms.remove(data.hologram);
        }
    }
}
