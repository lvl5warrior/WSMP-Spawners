package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SpawnerInfoGuiListener implements Listener {

    private final SpawnerManager manager;
    private final SpawnerInfoGui gui;
    private final ConfigManager config;

    public SpawnerInfoGuiListener(SpawnerManager manager, SpawnerInfoGui gui, ConfigManager config) {
        this.manager = manager;
        this.gui = gui;
        this.config = config;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerInfoGuiHolder holder)) return;

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof SpawnerInfoGuiHolder;

        if (!clickedTop) {
            // Clicked in the player's own inventory while this GUI is open -
            // block shift-clicks, which could otherwise deposit an item INTO
            // the spawner's storage grid. This GUI is output-only: you take
            // what the spawner made, you don't feed it your own items.
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getSlot();

        if (slot < SpawnerInfoGui.STORAGE_SLOTS_START) {
            event.setCancelled(true); // the whole top row is buttons/filler, never a real slot
            handleButtonClick(player, event.getInventory(), holder, slot);
            return;
        }

        // Storage grid: allow taking freely, block depositing. A held cursor
        // item would end up placed/swapped into the slot - that's the only
        // case we need to block; shift-clicking an item OUT has an empty
        // cursor and should proceed normally.
        ItemStack cursor = event.getCursor();
        boolean holdingSomething = cursor != null && cursor.getType() != Material.AIR;
        if (holdingSomething) {
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission("wsmpspawners.collectitems")) {
            event.setCancelled(true);
            player.sendMessage(config.message("no-permission-collectitems"));
        }
        // Otherwise: let the normal take/shift-click-out proceed uncancelled.
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerInfoGuiHolder holder)) return;

        List<ItemStack> finalContents = readStorageSlots(event.getInventory());
        // What the player actually removed = what was shown when they opened
        // it, minus what's still there now. Anything the live list gained
        // from a generation cycle WHILE this GUI was open was never part of
        // "opened", so it's correctly left alone rather than wiped out.
        List<ItemStack> taken = subtractGroupedTotals(groupByType(holder.openedSnapshot), groupByType(finalContents));
        for (ItemStack takenType : taken) {
            removeAmount(holder.data.storedItems, takenType, takenType.getAmount());
        }
        persist(holder);
    }

    private void handleButtonClick(Player player, Inventory inventory, SpawnerInfoGuiHolder holder, int slot) {
        SpawnerData data = holder.data;
        try {
            if (slot == SpawnerInfoGui.XP_BUTTON_SLOT) {
                if (!player.hasPermission("wsmpspawners.claimxp")) {
                    player.sendMessage(config.message("no-permission-claimxp"));
                    return;
                }
                if (data.storedXp < 1.0) {
                    player.sendMessage(config.message("not-enough-xp"));
                    return;
                }
                int wholeXp = (int) Math.floor(data.storedXp);
                data.storedXp -= wholeXp;
                player.giveExp(wholeXp);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.sendMessage(config.message("xp-claimed", "%amount%", String.valueOf(wholeXp)));

                inventory.setItem(SpawnerInfoGui.XP_BUTTON_SLOT, gui.buildXpButton(data));
                inventory.setItem(SpawnerInfoGui.INFO_ICON_SLOT, gui.buildInfoIcon(holder.spawnerLocation, data));
                persist(holder);

            } else if (slot == SpawnerInfoGui.ITEMS_BUTTON_SLOT) {
                if (!player.hasPermission("wsmpspawners.collectitems")) {
                    player.sendMessage(config.message("no-permission-collectitems"));
                    return;
                }

                List<ItemStack> toGive = new ArrayList<>();
                for (int i = SpawnerInfoGui.STORAGE_SLOTS_START; i < inventory.getSize(); i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        toGive.add(item);
                        inventory.setItem(i, null);
                    }
                }

                for (ItemStack item : toGive) {
                    var overflow = player.getInventory().addItem(item);
                    for (ItemStack leftover : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }

                // Remove exactly what was given (grouped by type) from the
                // LIVE list, not a blind wipe - a generation cycle could have
                // added more of something to data.storedItems in the instant
                // between this GUI opening and this button being clicked,
                // and that shouldn't vanish just because it wasn't visible
                // yet when the button was pressed.
                for (ItemStack givenType : groupByType(toGive)) {
                    removeAmount(data.storedItems, givenType, givenType.getAmount());
                }

                // Resync the baseline snapshot to match what's now actually
                // displayed (empty) - otherwise, if the GUI is closed later,
                // onClose's diff would compare against the ORIGINAL open-time
                // snapshot (which this button just partially consumed) and
                // incorrectly subtract a second time, silently deleting
                // anything a generation cycle added between this click and
                // the GUI eventually closing.
                holder.openedSnapshot = new ArrayList<>();

                player.sendMessage(config.message("gui-items-collected"));
                inventory.setItem(SpawnerInfoGui.ITEMS_BUTTON_SLOT, gui.buildItemsButton(data));
                inventory.setItem(SpawnerInfoGui.INFO_ICON_SLOT, gui.buildInfoIcon(holder.spawnerLocation, data));
                persist(holder);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Something went wrong: " + e.getMessage());
            player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error handling info GUI button for "
                    + player.getName() + ": " + e);
        }
    }

    private void persist(SpawnerInfoGuiHolder holder) {
        Block block = holder.spawnerLocation.getBlock();
        if (manager.isTrackedSpawnerBlock(block)) {
            manager.writeToBlock(block, holder.data);
        }
    }

    private List<ItemStack> readStorageSlots(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = SpawnerInfoGui.STORAGE_SLOTS_START; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    /** Consolidates a list into one stack per distinct item type, summing amounts. */
    private List<ItemStack> groupByType(List<ItemStack> items) {
        List<ItemStack> totals = new ArrayList<>();
        for (ItemStack item : items) {
            boolean merged = false;
            for (ItemStack existing : totals) {
                if (existing.isSimilar(item)) {
                    existing.setAmount(existing.getAmount() + item.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) totals.add(item.clone());
        }
        return totals;
    }

    /** Per type in `from`, how much is missing compared to `subtractedBy` (clamped at 0 - never negative). */
    private List<ItemStack> subtractGroupedTotals(List<ItemStack> from, List<ItemStack> subtractedBy) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack fromType : from) {
            int remainingAmount = 0;
            for (ItemStack otherType : subtractedBy) {
                if (otherType.isSimilar(fromType)) {
                    remainingAmount = otherType.getAmount();
                    break;
                }
            }
            int taken = fromType.getAmount() - remainingAmount;
            if (taken > 0) {
                ItemStack takenStack = fromType.clone();
                takenStack.setAmount(taken);
                result.add(takenStack);
            }
        }
        return result;
    }

    /** Removes up to amountToRemove of a matching item type from the live list, deleting stacks that hit zero. */
    private void removeAmount(List<ItemStack> live, ItemStack type, int amountToRemove) {
        int remaining = amountToRemove;
        var iterator = live.iterator();
        while (iterator.hasNext() && remaining > 0) {
            ItemStack stack = iterator.next();
            if (!stack.isSimilar(type)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) iterator.remove();
        }
    }
}
