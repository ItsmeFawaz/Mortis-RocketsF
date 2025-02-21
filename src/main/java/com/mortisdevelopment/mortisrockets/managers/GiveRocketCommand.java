package com.mortisdevelopment.mortisrockets.managers;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GiveRocketCommand implements CommandExecutor {
    private final Manager manager;

    public GiveRocketCommand(Manager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if(!(commandSender instanceof Player)) {
            commandSender.sendMessage(manager.getRocketManager().getMessage("NO_PERMISSION"));
            return true;
        }
        Player player = (Player) commandSender;
        player.getInventory().addItem(manager.getRocketManager().getSettings().getInventoryItem());
        return true;
    }
}
