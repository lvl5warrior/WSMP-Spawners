package com.warriorssmp.passivespawners;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public class SpawnerAdminCommand implements CommandExecutor {

    private final SpawnerManager manager;
    private final SpawnerItemFactory itemFactory;
    private final SpawnerGui gui;

    public SpawnerAdminCommand(SpawnerManager manager, SpawnerItemFactory itemFactory, SpawnerGui gui) {
        this.manager = manager;
        this.itemFactory = itemFactory;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("spawners")) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /wsmpadmin spawners give <player> <mob_type> [level]");
            sender.sendMessage(ChatColor.GRAY + "       /wsmpadmin spawners list [world]");
            sender.sendMessage(ChatColor.GRAY + "       /wsmpadmin spawners gui");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /wsmpadmin spawners give|list|gui ...");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "gui" -> handleGui(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use 'give', 'list', or 'gui'.");
                yield true;
            }
        };
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can open the spawner GUI.");
            return true;
        }
        if (!gui.hasAnyEnabledMobs()) {
            player.sendMessage(ChatColor.RED + "No mob types are currently enabled in spawner-registry (config.yml) - "
                    + "there's nothing to show in the GUI yet.");
            return true;
        }
        try {
            player.openInventory(gui.build(0));
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Something went wrong opening the spawner GUI: " + e.getMessage());
            player.getServer().getLogger().warning("[WSMP-PassiveSpawners] Error building spawner GUI for "
                    + player.getName() + ": " + e);
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /wsmpadmin spawners give <player> <mob_type> [level]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' isn't online.");
            return true;
        }

        EntityType mobType;
        try {
            mobType = EntityType.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "'" + args[3] + "' isn't a valid mob type.");
            return true;
        }

        int level = 1;
        if (args.length >= 5) {
            try {
                level = Math.max(1, Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "'" + args[4] + "' isn't a valid level number.");
                return true;
            }
        }

        ItemStack item = itemFactory.create(mobType, level);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        }

        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a level " + level + " "
                + NameUtil.prettify(mobType) + " spawner.");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        String worldFilter = args.length >= 3 ? args[2] : null;

        sender.sendMessage(ChatColor.GRAY + "Tracked spawners (currently loaded chunks only):");
        int count = 0;
        for (Map.Entry<Location, SpawnerData> entry : manager.allTracked().entrySet()) {
            Location loc = entry.getKey();
            if (worldFilter != null && !loc.getWorld().getName().equalsIgnoreCase(worldFilter)) continue;

            SpawnerData data = entry.getValue();
            sender.sendMessage(ChatColor.WHITE + " - " + loc.getWorld().getName() + " ("
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ") "
                    + ChatColor.GRAY + NameUtil.prettify(data.mobType) + " Lv." + data.level
                    + " | stored XP: " + String.format(Locale.ROOT, "%.1f", data.storedXp));
            count++;
        }
        sender.sendMessage(ChatColor.GRAY + "Total: " + count
                + (worldFilter != null ? " in " + worldFilter : "") + ".");
        return true;
    }
}
