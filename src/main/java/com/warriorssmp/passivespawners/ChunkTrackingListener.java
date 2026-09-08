package com.warriorssmp.passivespawners;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * The block's own PersistentDataContainer is always the real source of
 * truth for a spawner's data - this listener is just what discovers spawner
 * blocks as their chunks load (so the tick loop has them in memory) and lets
 * them go again when the chunk unloads (since an unloaded chunk isn't
 * simulated anyway, there's nothing useful to keep tracking).
 */
public class ChunkTrackingListener implements Listener {

    private final SpawnerManager manager;
    private final HologramManager holograms;

    public ChunkTrackingListener(SpawnerManager manager, HologramManager holograms) {
        this.manager = manager;
        this.holograms = holograms;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Sweep first - any leftover hologram from a spawner that was
        // removed by something bypassing our own listeners entirely (a
        // /setblock, WorldEdit, another plugin) would otherwise sit here
        // forever, overlapping with whatever gets spawned fresh below.
        holograms.sweepOrphans(event.getChunk());

        for (BlockState state : event.getChunk().getTileEntities()) {
            if (!(state instanceof CreatureSpawner)) continue;
            Block block = state.getBlock();
            SpawnerData data = manager.readFromBlock(block);
            if (data == null) continue; // a normal, non-WSMP spawner - leave it alone entirely

            manager.register(block.getLocation(), data);
            data.hologram = holograms.spawn(block.getLocation(), data.mobType, data.level);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (!(state instanceof CreatureSpawner)) continue;
            Block block = state.getBlock();
            SpawnerData data = manager.unregister(block.getLocation());
            if (data != null && data.hologram != null) {
                holograms.remove(data.hologram);
            }
        }
    }
}
