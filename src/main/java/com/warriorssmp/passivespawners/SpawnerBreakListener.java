package com.warriorssmp.passivespawners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class SpawnerBreakListener implements Listener {

    private final SpawnerManager manager;
    private final SpawnerItemFactory itemFactory;
    private final HologramManager holograms;
    private final ConfigManager config;

    public SpawnerBreakListener(SpawnerManager manager, SpawnerItemFactory itemFactory,
                                 HologramManager holograms, ConfigManager config) {
        this.manager = manager;
        this.itemFactory = itemFactory;
        this.holograms = holograms;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isTrackedSpawnerBlock(block)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("wsmpspawners.break")) {
            event.setCancelled(true);
            player.sendMessage(config.message("no-permission-break"));
            return;
        }

        SpawnerData data = manager.get(block.getLocation());
        if (data == null) {
            // Shouldn't normally happen (the block is tracked so its chunk is
            // loaded, so it should be in memory) but fall back to reading
            // straight from the block's PDC rather than losing the drop.
            data = manager.readFromBlock(block);
        }

        // We decide exactly what drops ourselves, based on break-behavior -
        // never let vanilla's own default spawner drop (nothing, normally) happen.
        event.setDropItems(false);

        if (data != null) {
            try {
                Integer dropLevel = switch (config.breakBehavior()) {
                    case RETURN_LEVEL_1 -> 1;
                    case RETURN_CURRENT_LEVEL -> data.level;
                    case DESTROY -> null;
                };

                if (dropLevel != null) {
                    var drop = itemFactory.create(data.mobType, dropLevel);
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }

                if (data.hologram != null) {
                    holograms.remove(data.hologram);
                }
            } catch (Exception e) {
                player.sendMessage(org.bukkit.ChatColor.RED + "That spawner broke, but something went wrong finishing "
                        + "cleanup: " + e.getMessage());
                player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error cleaning up broken spawner at "
                        + block.getLocation() + ": " + e);
            }
        }

        manager.unregister(block.getLocation());
    }
}
