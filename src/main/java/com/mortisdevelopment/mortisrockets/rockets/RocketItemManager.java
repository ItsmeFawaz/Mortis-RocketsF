package com.mortisdevelopment.mortisrockets.rockets;

import com.mortisdevelopment.mortisrockets.managers.Manager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RocketItemManager implements CommandExecutor, Listener {
    private final Manager manager;
    private final HashMap<UUID, Rocket> sitting;
    private final Set<ArmorStand> rockets;

    public RocketItemManager(Manager manager) {
        this.manager = manager;
        sitting = new HashMap<>();
        rockets = new HashSet<>();
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
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        //If the player is not right clicking a block, return
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        //If the player is not holding the rocket item, return
        if ((event.getHand() == EquipmentSlot.HAND && !player.getInventory().getItemInMainHand().isSimilar(manager.getRocketManager().getSettings().getInventoryItem())) || (event.getHand() == EquipmentSlot.OFF_HAND && !player.getInventory().getItemInOffHand().isSimilar(manager.getRocketManager().getSettings().getInventoryItem())))
            return;


        if(event.getBlockFace() != BlockFace.UP) {
            //CANNOT PLACE OTHER THAN TOP MESSAGE
            return;
        }
        //Place rocket
        ArmorStand rocket = spawnRocket(event.getClickedBlock().getLocation().add(0.5, 1, 0.5));
        rockets.add(rocket);
        Bukkit.broadcastMessage("BlockFace: " + event.getBlockFace());
    }
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if(!(event.getRightClicked() instanceof ArmorStand))
            return;
        ArmorStand stand = (ArmorStand) event.getRightClicked();
        if(!rockets.contains(stand))
            return;
        event.setCancelled(true);
        stand.addPassenger(event.getPlayer());
        //
    }
    public ArmorStand spawnRocket(Location location) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.getEquipment().setHelmet(manager.getRocketManager().getSettings().getLaunchItem(), true);
        stand.addDisabledSlots(EquipmentSlot.HEAD);
        stand.setCanPickupItems(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCanMove(true);
        stand.setAI(true);
        stand.setInvisible(true);
        return stand;
    }
}
