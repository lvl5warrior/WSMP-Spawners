package com.warriorssmp.passivespawners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A plain marker so SpawnerGuiListener can identify "is this inventory ours"
 * reliably via instanceof, rather than matching on title text - the safer,
 * standard approach since title strings can coincidentally collide with
 * other plugins' GUIs.
 */
public class SpawnerGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
