package com.warriorssmp.passivespawners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

/**
 * Runtime state for a single tracked spawner. The authoritative copy of
 * mobType/level/storedXp always lives in the block's own PersistentDataContainer
 * (see SpawnerManager) - this object is a fast in-memory mirror kept for
 * currently-loaded chunks only, rebuilt from the block's PDC on chunk load.
 */
public class SpawnerData {

    public EntityType mobType;
    public int level;
    public double storedXp;
    public long lastGenerationTick;

    // Not persisted - the hologram is deliberately non-persistent (see
    // HologramManager) and gets recreated fresh every time its chunk loads.
    public transient ArmorStand hologram;

    public SpawnerData(EntityType mobType, int level, double storedXp, long lastGenerationTick) {
        this.mobType = mobType;
        this.level = level;
        this.storedXp = storedXp;
        this.lastGenerationTick = lastGenerationTick;
    }
}
