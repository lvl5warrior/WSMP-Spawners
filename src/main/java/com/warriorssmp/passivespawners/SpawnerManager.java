package com.warriorssmp.passivespawners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class SpawnerManager {

    private final JavaPlugin plugin;
    private final SpawnerKeys keys;
    private final ConfigManager config;

    // Only holds spawners in CURRENTLY LOADED chunks - see ChunkTrackingListener.
    // The block's own PersistentDataContainer is always the source of truth;
    // this map exists purely so the per-tick generation loop doesn't have to
    // touch block state (a much heavier operation) every tick for every spawner.
    private final Map<Location, SpawnerData> tracked = new HashMap<>();

    private long currentTick = 0L;

    public SpawnerManager(JavaPlugin plugin, SpawnerKeys keys, ConfigManager config) {
        this.plugin = plugin;
        this.keys = keys;
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Block <-> PDC persistence
    // ------------------------------------------------------------------

    public boolean isTrackedSpawnerBlock(Block block) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return false;
        return spawner.getPersistentDataContainer().has(keys.blockMobType, PersistentDataType.STRING);
    }

    /** Reads a SpawnerData out of a block's PDC, or null if this isn't one of ours. */
    public SpawnerData readFromBlock(Block block) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return null;
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        String mobName = pdc.get(keys.blockMobType, PersistentDataType.STRING);
        if (mobName == null) return null;

        EntityType mobType;
        try {
            mobType = EntityType.valueOf(mobName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Spawner at " + describeLocation(block.getLocation())
                    + " has an unreadable mob type '" + mobName + "' - ignoring it.");
            return null;
        }

        int level = pdc.getOrDefault(keys.blockLevel, PersistentDataType.INTEGER, 1);
        double storedXp = pdc.getOrDefault(keys.blockStoredXp, PersistentDataType.DOUBLE, 0.0);
        // Deliberately NOT persisting lastGenerationTick's absolute value across
        // restarts - our tick counter itself resets to 0 on every boot, so an
        // old absolute tick value would be meaningless against it. Every
        // spawner just starts its interval fresh on server start, which is a
        // minor, harmless simplification.
        return new SpawnerData(mobType, level, storedXp, currentTick);
    }

    /** Writes a SpawnerData's persistent fields into a block's PDC. */
    public void writeToBlock(Block block, SpawnerData data) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        pdc.set(keys.blockMobType, PersistentDataType.STRING, data.mobType.name());
        pdc.set(keys.blockLevel, PersistentDataType.INTEGER, data.level);
        pdc.set(keys.blockStoredXp, PersistentDataType.DOUBLE, data.storedXp);
        spawner.update();
    }

    private String describeLocation(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ------------------------------------------------------------------
    // In-memory tracking (loaded chunks only)
    // ------------------------------------------------------------------

    public void register(Location location, SpawnerData data) {
        tracked.put(location, data);
    }

    public SpawnerData unregister(Location location) {
        return tracked.remove(location);
    }

    public SpawnerData get(Location location) {
        return tracked.get(location);
    }

    /** For the admin list command - only reflects currently-loaded chunks. */
    public Map<Location, SpawnerData> allTracked() {
        return tracked;
    }

    public long currentTick() {
        return currentTick;
    }

    // ------------------------------------------------------------------
    // Generation loop
    // ------------------------------------------------------------------

    /**
     * Called once per server tick. onGenerate is invoked for any spawner whose
     * interval has elapsed - kept as a callback rather than baked in here so
     * this class doesn't need to depend on LootGenerator/LootRouter directly.
     */
    public void tick(BiConsumer<Location, SpawnerData> onGenerate) {
        currentTick++;
        // Snapshot first - the callback can end up calling unregister() (e.g.
        // PassiveSpawnersPlugin.onGenerate does, if a block changed out from
        // under us), which would modify `tracked` while we're mid-iteration
        // over it otherwise.
        for (Map.Entry<Location, SpawnerData> entry : new java.util.ArrayList<>(tracked.entrySet())) {
            SpawnerData data = entry.getValue();
            ConfigManager.MobConfig mobConfig = config.get(data.mobType);
            if (mobConfig == null || !mobConfig.enabled()) continue;

            int effectiveInterval = Math.max(1, mobConfig.generationIntervalTicks()
                    - (data.level - 1) * config.intervalReductionPerLevelTicks());

            if (currentTick - data.lastGenerationTick >= effectiveInterval) {
                data.lastGenerationTick = currentTick;
                onGenerate.accept(entry.getKey(), data);
            }
        }
    }
}
