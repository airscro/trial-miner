package com.example.trialminer;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class TrialMinerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new TrialSpawnerListener(this), this);
        getLogger().info("TrialMiner enabled. Trial spawners can now be mined with their state preserved.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("trialminer")) {
            return false;
        }
        if (args.length == 0) {
            if (sender.hasPermission("trialminer.admin")) {
                sender.sendMessage("§e/trialminer reload §7- reload config");
                sender.sendMessage("§e/trialminer give §7- give yourself a trial spawner");
            } else {
                sender.sendMessage("§7TrialMiner: mine trial spawners with Silk Touch.");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("trialminer.admin")) {
                    sender.sendMessage("§cYou don't have permission to do that.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage("§aTrialMiner config reloaded.");
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("trialminer.admin")) {
                    sender.sendMessage("§cYou don't have permission to do that.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can use /trialminer give.");
                    return true;
                }
                player.getInventory().addItem(new ItemStack(Material.TRIAL_SPAWNER));
                player.sendMessage("§aGave you a trial spawner. Place it, configure it, mine it back.");
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use /trialminer for help.");
                return true;
            }
        }
    }
}
