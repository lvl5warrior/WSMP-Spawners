package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
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
public class HologramManager implements Listener {

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
    // Bedrock visibility - allow-bedrock-holograms: false in config.yml -
    // folded directly into updateVisibilityForPlayer() below, alongside
    // line-of-sight and closest-only logic, rather than a separate pass.
    // ------------------------------------------------------------------

    /**
     * Player-centric, not hologram-centric: shows only the SINGLE closest
     * visible tracked spawner's hologram to this player, hiding every other
     * one. Two spawners placed near each other previously showed both
     * holograms simultaneously, and their text visually collided into
     * unreadable overlap - this makes that structurally impossible, since
     * only ever one hologram is shown to any given player at a time.
     */
    // Tracks what's currently shown to each player, so the common case
    // (nothing changed since the last check) does zero show/hide calls
    // instead of unconditionally re-calling the API for every spawner on
    // every player on every interval tick - a real cost at scale (100
    // spawners x 10 players = 1000 calls every half-second, for no reason,
    // if this weren't here).
    private final java.util.Map<java.util.UUID, ArmorStand> lastShownHologram = new java.util.HashMap<>();

    public void updateVisibilityForPlayer(Player player, java.util.Collection<SpawnerData> allSpawners) {
        double radiusSquared = (double) config.hologramVisibilityCheckRadius() * config.hologramVisibilityCheckRadius();
        boolean bedrockAllowed = config.allowBedrockHolograms() || !isBedrockPlayer(player);

        ArmorStand closest = null;
        double closestDistSq = Double.MAX_VALUE;

        if (bedrockAllowed) {
            for (SpawnerData data : allSpawners) {
                ArmorStand hologram = data.hologram;
                if (hologram == null || hologram.isDead()) continue;
                if (!hologram.getWorld().equals(player.getWorld())) continue;

                double distSq = player.getLocation().distanceSquared(hologram.getLocation());
                if (distSq > radiusSquared) continue;
                if (!player.hasLineOfSight(hologram)) continue;

                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = hologram;
                }
            }
        }

        ArmorStand previouslyShown = lastShownHologram.get(player.getUniqueId());
        boolean previousStillValid = previouslyShown != null && !previouslyShown.isDead();

        if (closest == (previousStillValid ? previouslyShown : null)) {
            if (previouslyShown != null && !previousStillValid) {
                lastShownHologram.remove(player.getUniqueId()); // clean up a stale dead reference even when nothing else changed
            }
            return; // nothing changed since last check - skip all API calls entirely
        }

        if (previousStillValid) {
            player.hideEntity(plugin, previouslyShown);
        }
        if (closest != null) {
            player.showEntity(plugin, closest);
            lastShownHologram.put(player.getUniqueId(), closest);
        } else {
            lastShownHologram.remove(player.getUniqueId());
        }
    }

    /** Called on player disconnect - without this, the cache above would grow forever across every player who's ever joined, a slow but genuine memory leak on a long-running server. */
    public void forgetPlayer(java.util.UUID playerId) {
        lastShownHologram.remove(playerId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        forgetPlayer(event.getPlayer().getUniqueId());
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
