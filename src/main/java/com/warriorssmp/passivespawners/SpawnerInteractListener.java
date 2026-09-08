package com.warriorssmp.passivespawners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class SpawnerInteractListener implements Listener {

    private final SpawnerManager manager;
    private final ConfigManager config;

    public SpawnerInteractListener(SpawnerManager manager, ConfigManager config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // avoid firing twice (main hand + off hand)

        Block block = event.getClickedBlock();
        if (block == null || !manager.isTrackedSpawnerBlock(block)) return;

        Player player = event.getPlayer();
        boolean emptyHand = player.getInventory().getItemInMainHand().getType() == Material.AIR;
        if (!emptyHand) return; // let a held item's own interaction (e.g. another spawner item for stacking) proceed normally

        event.setUseInteractedBlock(Event.Result.DENY);

        if (!player.hasPermission("wsmpspawners.claimxp")) {
            player.sendMessage(config.message("no-permission-claimxp"));
            return;
        }

        SpawnerData data = manager.get(block.getLocation());
        if (data == null) {
            data = manager.readFromBlock(block);
        }
        if (data == null) return;

        if (data.storedXp < 1.0) {
            player.sendMessage(config.message("not-enough-xp"));
            return;
        }

        int wholeXp = (int) Math.floor(data.storedXp);
        data.storedXp -= wholeXp;

        player.giveExp(wholeXp);
        manager.writeToBlock(block, data);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(config.message("xp-claimed", "%amount%", String.valueOf(wholeXp)));
    }
}
