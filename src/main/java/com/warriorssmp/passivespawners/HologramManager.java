package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Holograms here are real (but minimal) ArmorStand entities - invisible,
 * marker mode, no gravity, non-persistent - NOT true packet-only fake
 * entities. A genuine packet-level hologram would need NMS code specific to
 * this exact Paper build or a library like ProtocolLib, neither of which
 * this plugin depends on. This tradeoff is deliberate: a handful of nearly
 * inert marker armor stands cost essentially nothing next to the hundreds
 * of real, AI-ticking mob entities this whole plugin exists to eliminate.
 */
public class HologramManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final SpawnerKeys keys;

    public HologramManager(JavaPlugin plugin, ConfigManager config, SpawnerKeys keys) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
    }

    public ArmorStand spawn(Location spawnerLocation, EntityType mobType, int level) {
        Location hologramLoc = spawnerLocation.clone().add(0.5, config.hologramHeightOffset(), 0.5);
        ArmorStand stand = hologramLoc.getWorld().spawn(hologramLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSmall(true);
            as.setCustomNameVisible(true);
            as.setPersistent(false); // recreated fresh on every chunk load, see ChunkTrackingListener
            as.setCanTick(false);
            as.getPersistentDataContainer().set(keys.hologramMarker, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
        updateName(stand, mobType, level);
        applyBedrockVisibility(stand);
        return stand;
    }

    public void updateName(ArmorStand hologram, EntityType mobType, int level) {
        String mobName = NameUtil.prettify(mobType);
        String text = ChatColor.translateAlternateColorCodes('&',
                "&7[Lv.&6" + level + "&7] &f" + mobName + " Spawner");
        hologram.setCustomName(text);
    }

    public void remove(ArmorStand hologram) {
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
    }

    /**
     * Removes every marked hologram entity currently in a chunk, no matter
     * how it got there or whether we still have a live reference to it.
     * Called before re-registering a chunk's spawners on load, so that a
     * spawner removed by something that bypasses our own listeners entirely
     * (a /setblock, WorldEdit, another plugin directly editing the block)
     * can never leave a permanently orphaned hologram behind - overlapping
     * with whatever gets spawned fresh moments later.
     */
    public void sweepOrphans(org.bukkit.Chunk chunk) {
        for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            Byte marker = stand.getPersistentDataContainer().get(keys.hologramMarker, org.bukkit.persistence.PersistentDataType.BYTE);
            if (marker != null) {
                stand.remove();
            }
        }
    }

    /**
     * True if this hologram reference is still a real, live entity. Something
     * outside our control - an admin running /kill @e[type=armor_stand], a
     * plugin conflict, a datapack, anything - could remove it without ever
     * going through our own code, leaving a stale reference behind. Callers
     * that are about to update a hologram's name should check this first and
     * respawn a fresh one if it's no longer valid, rather than operate on a
     * dead entity.
     */
    public boolean isValid(ArmorStand hologram) {
        return hologram != null && !hologram.isDead() && hologram.isValid();
    }


    // ------------------------------------------------------------------
    // Bedrock visibility - allow-bedrock-holograms: false in config.yml
    // ------------------------------------------------------------------

    private void applyBedrockVisibility(ArmorStand hologram) {
        if (config.allowBedrockHolograms()) return;
        for (Player player : hologram.getWorld().getPlayers()) {
            if (isBedrockPlayer(player)) {
                player.hideEntity(plugin, hologram);
            }
        }
    }

    /**
     * The combined visibility check, called periodically (not just once at
     * creation) - a player's line of sight to a hologram changes constantly
     * as they move, so this has to be re-evaluated on a schedule, not just
     * decided once when the hologram spawns.
     */
    public void updateVisibility(ArmorStand hologram) {
        if (hologram == null || hologram.isDead()) return;
        double radiusSquared = (double) config.hologramVisibilityCheckRadius() * config.hologramVisibilityCheckRadius();

        for (Player player : hologram.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(hologram.getLocation()) > radiusSquared) continue;

            boolean bedrockAllowed = config.allowBedrockHolograms() || !isBedrockPlayer(player);
            boolean lineOfSightClear = player.hasLineOfSight(hologram);
            boolean shouldSee = bedrockAllowed && lineOfSightClear;

            if (shouldSee) {
                player.showEntity(plugin, hologram);
            } else {
                player.hideEntity(plugin, hologram);
            }
        }
    }

    /** Called from a PlayerJoinEvent listener so holograms that already existed get hidden from a newly-joining Bedrock player too. */
    public void applyVisibilityForJoiningPlayer(Player player, Iterable<ArmorStand> allHolograms) {
        if (config.allowBedrockHolograms()) return;
        if (!isBedrockPlayer(player)) return;
        for (ArmorStand hologram : allHolograms) {
            player.hideEntity(plugin, hologram);
        }
    }

    /**
     * Reflection-based Floodgate detection - deliberately not a hard compile
     * dependency (Floodgate is only a softdepend), same approach used
     * elsewhere in this project for optional Bedrock integration. Returns
     * false (treat as a normal Java player) if Floodgate isn't installed or
     * its API isn't available for any reason.
     */
    private boolean isBedrockPlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
