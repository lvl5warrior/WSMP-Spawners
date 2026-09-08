package com.warriorssmp.passivespawners;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    public enum BreakBehavior { RETURN_LEVEL_1, DESTROY, RETURN_CURRENT_LEVEL }

    public record MobConfig(boolean enabled, double baseXpReward, int generationIntervalTicks) {}

    // --- global-settings ---
    private int maxSpawnerLevel;
    private boolean allowBedrockHolograms;
    private BreakBehavior breakBehavior;
    private int defaultGenerationIntervalTicks;
    private double hologramHeightOffset;
    private int maxStoredItemStacks;
    private int hologramVisibilityCheckRadius;
    private int hologramVisibilityCheckIntervalTicks;

    // --- scaling-modifiers ---
    private double lootMultiplierPerLevel;
    private double xpMultiplierPerLevel;
    private int intervalReductionPerLevelTicks;

    // --- messages ---
    private final Map<String, String> messages = new java.util.HashMap<>();

    // --- spawner-registry ---
    private final Map<EntityType, MobConfig> registry = new EnumMap<>(EntityType.class);

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var config = plugin.getConfig();

        ConfigurationSection globalSection = config.getConfigurationSection("global-settings");
        maxSpawnerLevel = globalSection != null ? globalSection.getInt("max-spawner-level", 50) : 50;
        if (maxSpawnerLevel < 1) {
            plugin.getLogger().warning("global-settings.max-spawner-level was " + maxSpawnerLevel
                    + " - clamping to 1 so upgrades aren't blocked entirely.");
            maxSpawnerLevel = 1;
        }
        allowBedrockHolograms = globalSection != null && globalSection.getBoolean("allow-bedrock-holograms", false);
        defaultGenerationIntervalTicks = globalSection != null
                ? globalSection.getInt("default-generation-interval-ticks", 200) : 200;
        hologramHeightOffset = globalSection != null ? globalSection.getDouble("hologram-height-offset", 1.2) : 1.2;
        maxStoredItemStacks = globalSection != null ? globalSection.getInt("max-stored-item-stacks", 45) : 45;
        if (maxStoredItemStacks < 1) {
            plugin.getLogger().warning("global-settings.max-stored-item-stacks was " + maxStoredItemStacks
                    + " - clamping to 1 so spawners can still hold at least something.");
            maxStoredItemStacks = 1;
        }
        hologramVisibilityCheckRadius = globalSection != null
                ? globalSection.getInt("hologram-visibility-check-radius", 48) : 48;
        hologramVisibilityCheckIntervalTicks = globalSection != null
                ? globalSection.getInt("hologram-visibility-check-interval-ticks", 10) : 10;
        if (hologramVisibilityCheckIntervalTicks < 1) {
            plugin.getLogger().warning("global-settings.hologram-visibility-check-interval-ticks was "
                    + hologramVisibilityCheckIntervalTicks + " - clamping to 1.");
            hologramVisibilityCheckIntervalTicks = 1;
        }
        String breakBehaviorRaw = globalSection != null
                ? globalSection.getString("break-behavior", "RETURN_CURRENT_LEVEL") : "RETURN_CURRENT_LEVEL";
        try {
            breakBehavior = BreakBehavior.valueOf(breakBehaviorRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid break-behavior '" + breakBehaviorRaw + "' - defaulting to RETURN_CURRENT_LEVEL.");
            breakBehavior = BreakBehavior.RETURN_CURRENT_LEVEL;
        }

        ConfigurationSection scalingSection = config.getConfigurationSection("scaling-modifiers");
        lootMultiplierPerLevel = scalingSection != null ? scalingSection.getDouble("loot-multiplier-per-level", 1.0) : 1.0;
        xpMultiplierPerLevel = scalingSection != null ? scalingSection.getDouble("xp-multiplier-per-level", 1.0) : 1.0;
        intervalReductionPerLevelTicks = scalingSection != null
                ? scalingSection.getInt("interval-reduction-per-level-ticks", 0) : 0;

        messages.clear();
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }

        registry.clear();
        ConfigurationSection registrySection = config.getConfigurationSection("spawner-registry");
        if (registrySection != null) {
            for (String mobKey : registrySection.getKeys(false)) {
                EntityType type;
                try {
                    type = EntityType.valueOf(mobKey.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("spawner-registry has an unknown mob type '" + mobKey + "' - skipping.");
                    continue;
                }
                ConfigurationSection mobSection = registrySection.getConfigurationSection(mobKey);
                if (mobSection == null) continue;
                boolean enabled = mobSection.getBoolean("enabled", true);
                double baseXp = mobSection.getDouble("base-xp-reward", 5.0);
                int interval = mobSection.getInt("generation-interval-ticks", defaultGenerationIntervalTicks);
                registry.put(type, new MobConfig(enabled, baseXp, interval));
            }
        }

        plugin.getLogger().log(Level.INFO, "Loaded {0} mob spawner definition(s).", registry.size());
    }

    public int maxSpawnerLevel() { return maxSpawnerLevel; }
    public boolean allowBedrockHolograms() { return allowBedrockHolograms; }
    public BreakBehavior breakBehavior() { return breakBehavior; }
    public double lootMultiplierPerLevel() { return lootMultiplierPerLevel; }
    public double xpMultiplierPerLevel() { return xpMultiplierPerLevel; }
    public int intervalReductionPerLevelTicks() { return intervalReductionPerLevelTicks; }
    public double hologramHeightOffset() { return hologramHeightOffset; }
    public int maxStoredItemStacks() { return maxStoredItemStacks; }
    public int hologramVisibilityCheckRadius() { return hologramVisibilityCheckRadius; }
    public int hologramVisibilityCheckIntervalTicks() { return hologramVisibilityCheckIntervalTicks; }

    /**
     * Looks up a message by key, translates '&' color codes, and replaces
     * %placeholder% tokens. Falls back to the key itself (so a missing/typo'd
     * message is obviously visible rather than silently blank) if it isn't
     * configured at all.
     */
    public String message(String key, String... placeholderPairs) {
        String raw = messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            raw = raw.replace(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    public MobConfig get(EntityType type) {
        return registry.get(type);
    }

    public boolean isRegistered(EntityType type) {
        MobConfig cfg = registry.get(type);
        return cfg != null && cfg.enabled();
    }

    public Map<EntityType, MobConfig> registry() {
        return registry;
    }
}
