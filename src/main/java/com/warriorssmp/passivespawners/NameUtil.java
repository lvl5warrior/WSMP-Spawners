package com.warriorssmp.passivespawners;

import org.bukkit.entity.EntityType;

import java.util.Locale;

public class NameUtil {

    private NameUtil() {}

    /** e.g. EntityType.ZOMBIE_VILLAGER -> "Zombie Villager" */
    public static String prettify(EntityType type) {
        String raw = type.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(raw.length());
        boolean capitalizeNext = true;
        for (char c : raw.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
            if (c == ' ') capitalizeNext = true;
        }
        return result.toString();
    }
}
