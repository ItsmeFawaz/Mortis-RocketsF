package com.mortisdevelopment.mortisrockets.rockets;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class RocketListener implements Listener {

    private final RocketManager rocketManager;
    private final HashMap<ArmorStand, Long> rockets;
    private final HashMap<Player, pendingRocketPlacement> rocketLocations;
    private final BukkitTask timeScheduler;

    public RocketListener(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        rockets = new HashMap<>();
        rocketLocations = new HashMap<>();
        timeScheduler = Bukkit.getScheduler().runTaskTimer(rocketManager.getPlugin(), () -> {
            //TODO:- Do not move message, placing down rocket
            // - Take the grace period time from config
            long currentTime = System.currentTimeMillis();
            rockets.entrySet().stream().forEach(x -> {
                if (x.getValue() != -1 && x.getValue() < currentTime) {
                    x.getKey().getLocation().getWorld().dropItem(x.getKey().getLocation(), rocketManager.getSettings().getInventoryItem());
                    rockets.remove(x.getKey());
                    x.getKey().remove();
                }
            });
            rocketLocations.entrySet().stream().filter(x -> x.getValue().getTime() < currentTime).forEach(x -> {
                ArmorStand rocket = rocketManager.spawnRocket(x.getValue().getLocation());
                rockets.put(rocket, currentTime + 20000);
                rocketLocations.remove(x.getKey());
                //TODO:- Rocket placement message
            });
        }, 0, 20);
    }
    //TODO:-
    // - Handle picking up rockets
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!rocketManager.getSettings().hasUrl()) {
            return;
        }
        e.getPlayer().setResourcePack(rocketManager.getSettings().getUrl());
    }
    @EventHandler
    public void onMount(EntityMountEvent evt) {
        ArmorStand rocket;
        if(evt.getMount() instanceof ArmorStand && rockets.containsKey((rocket = (ArmorStand) evt.getMount()))) {
            rockets.put(rocket, -1L);
        }
    }
    @EventHandler
    public void onDismount(EntityDismountEvent e) {

        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getEntity();
        ArmorStand rocket;
        if(e.getDismounted() instanceof ArmorStand && rockets.containsKey((rocket = (ArmorStand) e.getDismounted()))) {
            rockets.put(rocket, System.currentTimeMillis() + 20000);
            return;
        }
        if (!rocketManager.getTraveling().contains(player.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof ArmorStand))
            return;
        ArmorStand stand = (ArmorStand) event.getEntity();
        if(!rockets.containsKey(stand))
            return;
        event.setCancelled(true);
    }
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        //pseudo invulnerability for the armorstand.
        if(e.getEntity() instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) e.getEntity();
            if(rockets.containsKey(stand)) {
                e.setCancelled(true);
                return;
            }
        }
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getEntity();
        if (!rocketManager.getTraveling().contains(player.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if(!rocketLocations.containsKey(event.getPlayer()))
            return;
        if(event.getFrom().getX() == event.getTo().getX() || event.getFrom().getZ() == event.getTo().getZ())
            return;
        rocketLocations.remove(event.getPlayer());
        //TODO:- Rocket placement cancelled message
        event.getPlayer().sendMessage("§c§lRocket placement cancelled!");
        event.getPlayer().getInventory().addItem(rocketManager.getSettings().getInventoryItem());
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        //If the player is not right clicking a block, return
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();

        if(event.getBlockFace() != BlockFace.UP) {
            //TODO: CANNOT PLACE OTHER THAN TOP MESSAGE
            return;
        }

        if ((event.getHand() == EquipmentSlot.HAND && player.getInventory().getItemInMainHand().isSimilar(rocketManager.getSettings().getInventoryItem()))) {
            player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        } else if(event.getHand() == EquipmentSlot.OFF_HAND && player.getInventory().getItemInOffHand().isSimilar(rocketManager.getSettings().getInventoryItem())) {
            player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
        } else {
            return;
        }
        //TODO: - Check placement location, if blocks above
        rocketLocations.put(player, new pendingRocketPlacement(player, event.getClickedBlock().getLocation().add(0.5, 1, 0.5)));
        player.sendMessage("§a§lPlacing Rocket! Do not move for 5 seconds.");
        //TODO:- Rocket placement 5 second delay message, do not move
    }
    /*TODO:-anyone can sit in it, anyone can break it unless its occupied*/
    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        //If the player is not right clicking an Armor Stand, return
        if(!(event.getRightClicked() instanceof ArmorStand))
            return;

        ArmorStand stand = (ArmorStand) event.getRightClicked();

        //If the Armor Stand is not a rocket, return
        if(!rockets.containsKey(stand))
            return;

        event.setCancelled(true);
        stand.addPassenger(event.getPlayer());
    }
    private class pendingRocketPlacement {
        private final Player player;
        private final Location location;
        private final long time;

        public pendingRocketPlacement(Player player, Location location) {
            this.player = player;
            this.location = location;
            this.time = System.currentTimeMillis() + 5000;
        }


        public Player getPlayer() {
            return player;
        }

        public Location getLocation() {
            return location;
        }

        public long getTime() {
            return time;
        }
    }
}
