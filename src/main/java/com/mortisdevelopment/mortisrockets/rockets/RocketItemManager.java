package com.mortisdevelopment.mortisrockets.rockets;

import com.mortisdevelopment.mortisrockets.managers.Manager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class RocketItemManager implements CommandExecutor, Listener {
    private final Manager manager;

    public RocketItemManager(Manager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if(!(commandSender instanceof Player)) {
            commandSender.sendMessage(manager.getRocketManager().getMessage("NO_PERMISSION"));
            return true;
        }
        Player player = (Player) commandSender;
        player.getInventory().addItem(manager.getRocketManager().getSettings().getInventoryItem().clone());
        return true;
    }
}
