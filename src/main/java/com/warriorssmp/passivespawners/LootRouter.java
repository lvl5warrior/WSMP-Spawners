package com.warriorssmp.passivespawners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class LootRouter {

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /**
     * Tries every adjacent container face in turn, filling each as much as
     * possible before moving to the next. Anything that doesn't fit anywhere
     * (or if there's no container at all) drops on top of the spawner block.
     */
    public void route(Block spawnerBlock, List<ItemStack> items) {
        if (items.isEmpty()) return;

        List<ItemStack> remaining = items;

        for (BlockFace face : ADJACENT_FACES) {
            if (remaining.isEmpty()) break;
            Block adjacent = spawnerBlock.getRelative(face);
            if (!(adjacent.getState() instanceof Container container)) continue;

            var leftovers = container.getInventory().addItem(remaining.toArray(new ItemStack[0]));
            remaining = new java.util.ArrayList<>(leftovers.values());
        }

        if (!remaining.isEmpty()) {
            dropOnTop(spawnerBlock.getLocation(), remaining);
        }
    }

    private void dropOnTop(Location spawnerLocation, List<ItemStack> items) {
        Location dropAt = spawnerLocation.clone().add(0.5, 1.0, 0.5);
        for (ItemStack item : items) {
            dropAt.getWorld().dropItemNaturally(dropAt, item);
        }
    }
}
