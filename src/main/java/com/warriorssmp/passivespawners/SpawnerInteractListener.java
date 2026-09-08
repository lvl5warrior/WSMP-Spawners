package com.warriorssmp.passivespawners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a spawner with an empty hand opens the full info GUI
 * (replacing the older "instantly claim XP" shortcut - claiming is now one
 * of the buttons inside that GUI instead).
 *
 * Also blocks vanilla's own "right-click with a spawn egg to change this
 * spawner's mob type" mechanic entirely for our tracked spawners. The block
 * underneath is a real vanilla CreatureSpawner, so that mechanic is still
 * fully wired up by the game itself unless we explicitly stop it - and
 * while it wouldn't actually break generation (we never read vanilla's own
 * spawned-type, only our own PDC data), it WOULD create a confusing visual
 * mismatch between the rotating mob preview and what the spawner actually
 * produces. A spawner's mob type is fixed at placement and never changes.
 */
public class SpawnerInteractListener implements Listener {

    private final SpawnerManager manager;
    private final SpawnerInfoGui gui;
    private final ConfigManager config;

    public SpawnerInteractListener(SpawnerManager manager, SpawnerInfoGui gui, ConfigManager config) {
        this.manager = manager;
        this.gui = gui;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !manager.isTrackedSpawnerBlock(block)) return;

        Player player = event.getPlayer();

        // Checked against BOTH hands regardless of which hand this specific
        // event instance represents - Bukkit can fire a separate event for
        // the off-hand, and gating this check on event.getHand() alone would
        // let a spawn egg held in the off-hand slip through entirely while
        // something else (or nothing) sits in the main hand.
        boolean mainHandEgg = isSpawnEgg(player.getInventory().getItemInMainHand().getType());
        boolean offHandEgg = isSpawnEgg(player.getInventory().getItemInOffHand().getType());
        if (mainHandEgg || offHandEgg) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            player.sendMessage(config.message("spawn-egg-blocked"));
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return; // avoid opening the info GUI twice (main hand + off hand)

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        boolean emptyHand = heldItem.getType() == Material.AIR;
        if (!emptyHand) return; // let a held spawner item's own placement/stacking interaction proceed normally

        event.setUseInteractedBlock(Event.Result.DENY);

        SpawnerData data = manager.get(block.getLocation());
        if (data == null) {
            data = manager.readFromBlock(block);
        }
        if (data == null) return;

        try {
            player.openInventory(gui.build(block.getLocation(), data));
        } catch (Exception e) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Something went wrong opening the spawner info: " + e.getMessage());
            player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error opening info GUI at "
                    + block.getLocation() + " for " + player.getName() + ": " + e);
        }
    }

    private boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }
}
