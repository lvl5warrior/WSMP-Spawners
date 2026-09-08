package com.warriorssmp.passivespawners;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootRouter {

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /**
     * Tries every adjacent container face in turn, filling each as much as
     * possible before moving to the next. Returns whatever didn't fit
     * anywhere (or the original list unchanged, if there's no adjacent
     * container at all) - the caller decides what happens to that leftover
     * (accumulate it in the spawner's own storage, drop it, etc).
     */
    public List<ItemStack> route(Block spawnerBlock, List<ItemStack> items) {
        if (items.isEmpty()) return items;

        List<ItemStack> remaining = items;

        for (BlockFace face : ADJACENT_FACES) {
            if (remaining.isEmpty()) break;
            Block adjacent = spawnerBlock.getRelative(face);
            if (!(adjacent.getState() instanceof Container container)) continue;

            var leftovers = container.getInventory().addItem(remaining.toArray(new ItemStack[0]));
            remaining = new ArrayList<>(leftovers.values());
        }

        return remaining;
    }
}
