package com.mortisdevelopment.mortisrockets.rockets;

import lombok.Getter;
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
import java.util.Iterator;
import java.util.Map;

public class RocketListener implements Listener {

    private final RocketManager rocketManager;
    private final HashMap<Player, PendingRocketPlacement> rocketPlacements;
    private final HashMap<Player, PendingRocketPickup> rocketPickups;
    private final BukkitTask timeScheduler;

    public RocketListener(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        rocketPlacements = new HashMap<>();
        rocketPickups = new HashMap<>();
        timeScheduler = Bukkit.getScheduler().runTaskTimer(rocketManager.getPlugin(), () -> {
            //TODO: - Take the grace period time from config
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<ArmorStand, Long>> rocketsIterator = rocketManager.getPlacedRockets().entrySet().iterator();
            while (rocketsIterator.hasNext()) {
                Map.Entry<ArmorStand, Long> entry = rocketsIterator.next();
                if (entry.getValue() != -1 && entry.getValue() < currentTime) {
                    entry.getKey().getLocation().getWorld().dropItem(entry.getKey().getLocation(), rocketManager.getSettings().getInventoryItem());
                    rocketsIterator.remove();
                    entry.getKey().remove();
                }
            }
            Iterator<Map.Entry<Player, PendingRocketPlacement>> rocketLocationsIterator = rocketPlacements.entrySet().iterator();
            while (rocketLocationsIterator.hasNext()) {
                Map.Entry<Player, PendingRocketPlacement> entry = rocketLocationsIterator.next();
                if (entry.getValue().getTime() < currentTime) {
                    ArmorStand rocket = rocketManager.spawnRocket(entry.getValue().getLocation(), true);
                    rocketManager.getPlacedRockets().put(rocket, currentTime + (rocketManager.getSettings().getInactivityTime()*1000L));
                    rocketLocationsIterator.remove();
                }
            }
            Iterator<Map.Entry<Player, PendingRocketPickup>> rocketPickupIterator = rocketPickups.entrySet().iterator();
            while (rocketPickupIterator.hasNext()) {
                Map.Entry<Player, PendingRocketPickup> entry = rocketPickupIterator.next();
                if (entry.getValue().getTime() < currentTime) {
                    entry.getValue().getRocket().remove();
                    entry.getKey().getInventory().addItem(rocketManager.getSettings().getInventoryItem());
                    rocketManager.getPlacedRockets().remove(entry.getValue().getRocket());
                    rocketPickupIterator.remove();
                }
            }

        }, 0, 20);
    }
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
        if(evt.getMount() instanceof ArmorStand && rocketManager.getPlacedRockets().containsKey((rocket = (ArmorStand) evt.getMount()))) {
            rocketManager.getPlacedRockets().put(rocket, -1L);
        }
    }
    @EventHandler
    public void onDismount(EntityDismountEvent e) {

        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getEntity();
        ArmorStand rocket;
        if(e.getDismounted() instanceof ArmorStand && rocketManager.getPlacedRockets().containsKey((rocket = (ArmorStand) e.getDismounted()))) {
            rocketManager.getPlacedRockets().put(rocket, System.currentTimeMillis() + (rocketManager.getSettings().getInactivityTime()*1000L));
            return;
        }
        if (!rocketManager.getTraveling().contains(player.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof ArmorStand stand))
            return;
        if(!(event.getDamager() instanceof Player player))
            return;
        if(!rocketManager.getPlacedRockets().containsKey(stand))
            return;
        if(rocketPickups.containsKey(player) || isBeingPickedUp(stand)) {
            player.sendMessage(rocketManager.getMessage("ALREADY_PICKING_UP"));
            return;
        }

        rocketPickups.put(player, new PendingRocketPickup(stand));
        player.sendMessage(rocketManager.getMessage("PICKUP_ROCKET"));
        rocketManager.getPlacedRockets().put(stand, System.currentTimeMillis() + (rocketManager.getSettings().getInactivityTime()*1000L));
    }
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        //pseudo invulnerability for the armorstand.
        if(e.getEntity() instanceof ArmorStand stand) {
            if(rocketManager.getPlacedRockets().containsKey(stand)) {
                e.setCancelled(true);
                return;
            }
        }
        if (!(e.getEntity() instanceof Player player)) {
            return;
        }
        if (!rocketManager.getTraveling().contains(player.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        /*if(rocketManager.getLanding().containsKey(event.getPlayer().getUniqueId())) {
            final Vector vector = event.getPlayer().getLocation().toVector().subtract(rocketManager.getLanding().get(event.getPlayer().getUniqueId())).multiply(.8 * 1.1F);
            event.getPlayer().setVelocity(vector*//*.setY(-.2)*//*);
        }*/
        /*if(event.getFrom().getX() == event.getTo().getX() || event.getFrom().getZ() == event.getTo().getZ())
            return;*/
        if(!event.hasChangedBlock())
            return;
        if(rocketPlacements.containsKey(event.getPlayer())) {
            rocketPlacements.remove(event.getPlayer());
            event.getPlayer().sendMessage(rocketManager.getMessage("PICKUP_ROCKET_FAIL"));
            event.getPlayer().getInventory().addItem(rocketManager.getSettings().getInventoryItem());
        }
        if(rocketPickups.containsKey(event.getPlayer())) {

        }
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        //If the player is not right clicking a block, return
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();

        if ((event.getHand() == EquipmentSlot.HAND && player.getInventory().getItemInMainHand().isSimilar(rocketManager.getSettings().getInventoryItem()))) {
            if(event.getBlockFace() != BlockFace.UP) {
                event.getPlayer().sendMessage(rocketManager.getMessage("ROCKET_PLACE_NOT_TOP"));
                return;
            }
            player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        } else if(event.getHand() == EquipmentSlot.OFF_HAND && player.getInventory().getItemInOffHand().isSimilar(rocketManager.getSettings().getInventoryItem())) {
            if(event.getBlockFace() != BlockFace.UP) {
                event.getPlayer().sendMessage(rocketManager.getMessage("ROCKET_PLACE_NOT_TOP"));
                return;
            }
            player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
        } else {
            return;
        }
        //TODO: - Check placement location, if blocks above
        rocketPlacements.put(player, new PendingRocketPlacement(event.getClickedBlock().getLocation().add(0.5, 1, 0.5)));
        player.sendMessage(rocketManager.getMessage("PLACE_ROCKET"));
        //TODO:- Rocket placement 5 second delay message, do not move
    }
    /*TODO:-anyone can sit in it, anyone can break it unless its occupied*/
    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        //If the player is not right clicking an Armor Stand, return
        if(!(event.getRightClicked() instanceof ArmorStand stand))
            return;

        //If the Armor Stand is not a rocket, return
        if(!rocketManager.getPlacedRockets().containsKey(stand))
            return;

        event.setCancelled(true);
        stand.addPassenger(event.getPlayer());
    }
    private boolean isBeingPickedUp(ArmorStand rocket) {
        for(PendingRocketPickup pickup : rocketPickups.values()) {
            if(pickup.getRocket().equals(rocket))
                return true;
        }
        return false;
    }
    @Getter
    public class PendingRocketPlacement {
        private final Location location;
        private final long time;

        public PendingRocketPlacement(Location location) {
            this.location = location;
            this.time = System.currentTimeMillis() + (rocketManager.getSettings().getPlaceTime() * 1000L);
        }

    }
    @Getter
    public class PendingRocketPickup {
        //TODO: FIX MOVEMENT PREVENTING PICKUP
        private final ArmorStand rocket;
        private final long time;

        public PendingRocketPickup(ArmorStand rocket) {
            this.rocket = rocket;
            this.time = System.currentTimeMillis() + (rocketManager.getSettings().getPickupTime()* 1000L);
        }

    }
}
