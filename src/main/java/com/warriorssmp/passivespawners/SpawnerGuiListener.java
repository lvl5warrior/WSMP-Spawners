package com.warriorssmp.passivespawners;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnerGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final SpawnerItemFactory itemFactory;
    private final ConfigManager config;
    private final SpawnerGui gui;

    public SpawnerGuiListener(JavaPlugin plugin, SpawnerItemFactory itemFactory, ConfigManager config, SpawnerGui gui) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.config = config;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerGuiHolder holder)) return;

        // Purely a menu to press buttons on - never let anything actually
        // move in or out of it like a normal inventory. Applies to clicks in
        // either inventory while this GUI is open (a shift-click from the
        // player's own inventory could otherwise deposit an item here).
        event.setCancelled(true);

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof SpawnerGuiHolder;
        if (!clickedTop) return; // clicked their own inventory - already cancelled above, nothing else to do

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getSlot();
        if (slot == SpawnerGui.PREV_PAGE_SLOT) {
            openPage(player, holder.page - 1);
            return;
        }
        if (slot == SpawnerGui.NEXT_PAGE_SLOT) {
            openPage(player, holder.page + 1);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        EntityType mobType = itemFactory.readMobType(clicked);
        if (mobType == null) return; // clicked empty space, filler, or the page indicator

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

    /**
     * Deferred to the next tick rather than called synchronously here -
     * replacing an inventory from within its OWN click handler (this GUI
     * calling openInventory() on itself, mid-transaction) is a known Bukkit
     * pitfall that can cause an unreliable or glitchy reopen depending on
     * server version specifics. Waiting one tick sidesteps it entirely.
     */
    private void openPage(Player player, int page) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                player.openInventory(gui.build(page));
            } catch (Exception e) {
                player.sendMessage(org.bukkit.ChatColor.RED + "Something went wrong changing pages: " + e.getMessage());
                player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error opening give-GUI page " + page
                        + " for " + player.getName() + ": " + e);
            }
        });
    }
}
