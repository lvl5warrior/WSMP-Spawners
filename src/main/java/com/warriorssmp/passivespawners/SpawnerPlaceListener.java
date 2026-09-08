package com.warriorssmp.passivespawners;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerPlaceListener implements Listener {

    private final SpawnerManager manager;
    private final SpawnerItemFactory itemFactory;
    private final HologramManager holograms;
    private final ConfigManager config;

    public SpawnerPlaceListener(SpawnerManager manager, SpawnerItemFactory itemFactory,
                                 HologramManager holograms, ConfigManager config) {
        this.manager = manager;
        this.itemFactory = itemFactory;
        this.holograms = holograms;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack itemInHand = event.getItemInHand();
        EntityType mobType = itemFactory.readMobType(itemInHand);
        if (mobType == null) return; // not one of our spawner items - nothing to do

        Player player = event.getPlayer();
        if (!player.hasPermission("wsmpspawners.place")) {
            event.setCancelled(true);
            player.sendMessage(config.message("no-permission-place"));
            return;
        }

        int itemLevel = itemFactory.readLevel(itemInHand);
        Block against = event.getBlockAgainst();

        // Stacking: placing against an existing spawner of the SAME mob type
        // upgrades it by one level instead of placing a new block.
        if (manager.isTrackedSpawnerBlock(against)) {
            SpawnerData existing = manager.get(against.getLocation());
            if (existing != null && existing.mobType == mobType) {
                event.setCancelled(true);

                if (existing.level >= config.maxSpawnerLevel()) {
                    player.sendMessage(config.message("max-level-reached", "%level%", String.valueOf(config.maxSpawnerLevel())));
                    return;
                }

                if (!player.hasPermission("wsmpspawners.upgrade")) {
                    player.sendMessage(config.message("no-permission-upgrade"));
                    return;
                }

                existing.level += 1;
                manager.writeToBlock(against, existing);
                if (holograms.isValid(existing.hologram)) {
                    holograms.updateName(existing.hologram, existing.mobType, existing.level);
                } else {
                    // The old reference is stale (removed by something outside
                    // our control) - replace it rather than update a dead entity.
                    existing.hologram = holograms.spawn(against.getLocation(), existing.mobType, existing.level);
                }

                if (player.getGameMode() != GameMode.CREATIVE) {
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                }

                player.sendMessage(config.message("spawner-upgraded", "%level%", String.valueOf(existing.level)));
                return;
            }
        }

        // Normal new placement - initialize the freshly-placed block.
        Block placed = event.getBlockPlaced();
        try {
            SpawnerData data = new SpawnerData(mobType, Math.max(1, itemLevel), 0.0, manager.currentTick());
            manager.writeToBlock(placed, data);
            manager.register(placed.getLocation(), data);
            data.hologram = holograms.spawn(placed.getLocation(), mobType, data.level);
        } catch (Exception e) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "Something went wrong placing that spawner: " + e.getMessage());
            player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Failed to place spawner at "
                    + placed.getLocation() + ": " + e);
        }
    }
}
