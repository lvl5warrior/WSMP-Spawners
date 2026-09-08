package com.warriorssmp.passivespawners;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ItemSerialization {

    private ItemSerialization() {}

    public static String serialize(List<ItemStack> items) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(byteStream)) {
                dataOutput.writeInt(items.size());
                for (ItemStack item : items) {
                    dataOutput.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize stored spawner items", e);
        }
    }

    public static List<ItemStack> deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(raw));
            List<ItemStack> items = new ArrayList<>();
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(byteStream)) {
                int count = dataInput.readInt();
                for (int i = 0; i < count; i++) {
                    items.add((ItemStack) dataInput.readObject());
                }
            }
            return items;
        } catch (Exception e) {
            // Deliberately rethrown rather than swallowed here - the caller
            // (SpawnerManager.readFromBlock) has the block's location for a
            // much more useful warning message, and can decide to fall back
            // to an empty item list without losing the rest of that
            // spawner's data (level, XP, etc).
            throw new RuntimeException("Failed to deserialize stored spawner items", e);
        }
    }
}
