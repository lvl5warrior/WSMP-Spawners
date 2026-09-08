package com.warriorssmp.passivespawners;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerGuiListener implements Listener {

    private final SpawnerItemFactory itemFactory;
    private final ConfigManager config;

    public SpawnerGuiListener(SpawnerItemFactory itemFactory, ConfigManager config) {
        this.itemFactory = itemFactory;
        this.config = config;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Checked against the WHOLE VIEW's top inventory, not just whichever
        // specific inventory was clicked - a shift-click from the player's
        // OWN inventory (bottom) can still try to move an item into the top
        // inventory (ours), and that needs blocking too, even though the
        // click itself technically originated outside our GUI.
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerGuiHolder)) return;

        // event.getInventory() is documented ambiguously ("the primary
        // inventory involved") and can return the TOP inventory even for a
        // click that actually landed in the player's own inventory below -
        // event.getClickedInventory() is the one that actually reflects
        // which specific inventory the click landed in.
        boolean clickedOurGui = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof SpawnerGuiHolder;

        if (!clickedOurGui) {
            // Clicked in the player's own inventory while our GUI is open -
            // still need to block shift-clicks, which could otherwise move a
            // real item into one of our display slots. Anything else (normal
            // clicks entirely within their own inventory) is left alone.
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        // A direct click inside our GUI - always a "press this button", never
        // a real inventory transaction.
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        EntityType mobType = itemFactory.readMobType(clicked);
        if (mobType == null) return; // clicked empty space or something unrelated

        if (!(event.getWhoClicked() instanceof Player player)) return;

        try {
            int amount = event.isShiftClick() ? 64 : 1;
            ItemStack toGive = itemFactory.create(mobType, 1);
            toGive.setAmount(amount);

            var overflow = player.getInventory().addItem(toGive);
            if (!overflow.isEmpty()) {
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }

            player.sendMessage(config.message("gui-spawner-given",
                    "%amount%", String.valueOf(amount), "%mob%", NameUtil.prettify(mobType)));
        } catch (Exception e) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Something went wrong giving that spawner: " + e.getMessage());
            player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error giving spawner from GUI to "
                    + player.getName() + ": " + e);
        }
    }
}
