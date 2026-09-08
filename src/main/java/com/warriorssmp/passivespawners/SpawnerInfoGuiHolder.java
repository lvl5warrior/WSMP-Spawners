package com.warriorssmp.passivespawners;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Unlike the admin give-GUI (which is stateless - any click just reads the
 * clicked icon's own PDC), this GUI is tied to ONE specific spawner
 * instance, so the holder carries a direct reference to its data and
 * location for the buttons and the close-time persistence to act on.
 */
public class SpawnerInfoGuiHolder implements InventoryHolder {

    public final Location spawnerLocation;
    public final SpawnerData data;
    public java.util.List<org.bukkit.inventory.ItemStack> openedSnapshot = java.util.List.of();
    private Inventory inventory;

    public SpawnerInfoGuiHolder(Location spawnerLocation, SpawnerData data) {
        this.spawnerLocation = spawnerLocation;
        this.data = data;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
