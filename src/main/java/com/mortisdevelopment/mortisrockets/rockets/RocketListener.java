package com.mortisdevelopment.mortisrockets.rockets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.mortisdevelopment.mortisrockets.MortisRockets;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RocketListener implements Listener {

    private final RocketManager rocketManager;
    private final HashMap<Player, PendingRocketPlacement> rocketPlacements;
    private final HashMap<Player, PendingRocketPickup> rocketPickups;
    @Getter
    private final BukkitTask timeScheduler;
    private final ProtocolManager manager = ProtocolLibrary.getProtocolManager();

    public RocketListener(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        rocketPlacements = new HashMap<>();
        rocketPickups = new HashMap<>();

        manager.addPacketListener(new PacketAdapter(MortisRockets.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                RocketManager.TravelInfo travelInfo = rocketManager.getTraveling().get(event.getPlayer().getUniqueId());
                if (travelInfo == null || !travelInfo.isDropping()) {
                    return;
                }
                PacketContainer packet = event.getPacket();
                float sideways = packet.getFloat().read(0);
                float forward = packet.getFloat().read(1);
                performLandingMechanics(event.getPlayer(), travelInfo, sideways, forward);
            }
        });
        timeScheduler = Bukkit.getScheduler().runTaskTimer(rocketManager.getPlugin(), () -> {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<ArmorStand, Long>> rocketsIterator = rocketManager.getPlacedRockets().entrySet().iterator();
            while (rocketsIterator.hasNext()) {
                Map.Entry<ArmorStand, Long> entry = rocketsIterator.next();
                if(entry.getKey().isDead()) {
                    rocketsIterator.remove();
                    entry.getKey().remove();
                    continue;
                    
                }
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
                    rocketManager.getPlacedRockets().put(rocket, System.currentTimeMillis() + (rocketManager.getSettings().getInactivityTime()*1000L));
                    rocketLocationsIterator.remove();
                }
            }
            Iterator<Map.Entry<Player, PendingRocketPickup>> rocketPickupIterator = rocketPickups.entrySet().iterator();
            while (rocketPickupIterator.hasNext()) {
                Map.Entry<Player, PendingRocketPickup> entry = rocketPickupIterator.next();
                if (entry.getValue().getTime() < currentTime) {
                    entry.getValue().getRocket().getLocation().getWorld().dropItem(entry.getValue().getRocket().getLocation(), rocketManager.getSettings().getInventoryItem());
                    entry.getValue().getRocket().remove();
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
    public void onQuit(PlayerQuitEvent event) {
        if(rocketManager.getLaunchTasks().containsKey(event.getPlayer().getUniqueId())) {
            rocketManager.getLaunchTasks().remove(event.getPlayer().getUniqueId()).cancel();
            if(event.getPlayer().getVehicle() != null) {
                event.getPlayer().getVehicle().eject();
                event.getPlayer().getVehicle().remove();
            }
            event.getPlayer().getLocation().getWorld().dropItem(event.getPlayer().getLocation(), rocketManager.getSettings().getInventoryItem());
        }
        RocketManager.TravelInfo travel = rocketManager.getTraveling().remove(event.getPlayer().getUniqueId());
        if(travel != null) {
            switch (travel.getTravelStage()) {
                case LAUNCHING -> {
                    travel.getRunningTask().cancel();
                    event.getPlayer().teleport(travel.getLaunchingLocation());
                }
                case LANDING ->  {
                    travel.getRunningTask().cancel();
                    event.getPlayer().teleport(travel.getLandingLocation().add(0,2,0));
                }
                case DROP -> {
                    travel.getRunningTask().cancel();
                    event.getPlayer().teleport(travel.getStand().getLocation().getWorld().getHighestBlockAt(travel.getStand().getLocation()).getLocation().add(0,2,0));
                }
                case DISMOUNTING -> {
                    travel.getRunningTask().cancel();
                    travel.getStand().eject();
                }
            }
            if(rocketManager.getSettings().isDropRocketOnLand()) {
                event.getPlayer().getWorld().dropItem(travel.getStand().getLocation(), rocketManager.getSettings().getInventoryItem());
            }
            travel.getStand().remove();
        }
    }
    @EventHandler
    public void onMount(EntityMountEvent evt) {
        if(evt.getMount() instanceof ArmorStand rocket && rocketManager.getPlacedRockets().containsKey(rocket)) {
            Map.Entry<Player, PendingRocketPickup> pickup;
            if((pickup = isBeingPickedUp(rocket)) != null)
                rocketPickups.remove(pickup.getKey());
            rocketManager.getPlacedRockets().put(rocket, -1L);
        }
    }
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        RocketManager.TravelInfo travel = rocketManager.getTraveling().remove(event.getEntity().getUniqueId());
        if(travel != null) {
            travel.getRunningTask().cancel();
            travel.getStand().eject();
            travel.getStand().remove();
            if(rocketManager.getSettings().isDropRocketOnLand()) {
                event.getPlayer().getWorld().dropItem(travel.getStand().getLocation(), rocketManager.getSettings().getInventoryItem());
            }
        }
        if(rocketManager.getLaunchTasks().containsKey(event.getPlayer().getUniqueId())) {
            rocketManager.getLaunchTasks().remove(event.getPlayer().getUniqueId()).cancel();
            event.getPlayer().getVehicle().eject();
            event.getPlayer().getVehicle().remove();
            event.getPlayer().getLocation().getWorld().dropItem(event.getPlayer().getLocation(), rocketManager.getSettings().getInventoryItem());
        }
    }
    @EventHandler
    public void onDismount(EntityDismountEvent e) {

        if (!(e.getEntity() instanceof Player player)) {
            return;
        }
        if(rocketManager.getLaunchTasks().containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if(e.getDismounted() instanceof ArmorStand rocket && rocketManager.getPlacedRockets().containsKey(rocket) && rocketManager.getPlacedRockets().get(rocket) == -1) {
            rocketManager.getPlacedRockets().put(rocket, System.currentTimeMillis() + (rocketManager.getSettings().getInactivityTime()*1000L));
        }
        if (!rocketManager.getTraveling().containsKey(player.getUniqueId())) {
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
        if(rocketPickups.containsKey(player) || isBeingPickedUp(stand) != null) {
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
        if(rocketManager.getSettings().isLaunchInvincibility() && rocketManager.getLaunchTasks().containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        RocketManager.TravelInfo travelInfo = rocketManager.getTraveling().get(player.getUniqueId());
        if (travelInfo == null) {
            return;
        }
        if(!rocketManager.getSettings().isProtectWhileDismount() && travelInfo.isDismounting()) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if(!event.hasChangedBlock())
            return;
        if(rocketPlacements.containsKey(event.getPlayer())) {
            rocketPlacements.remove(event.getPlayer());
            event.getPlayer().sendMessage(rocketManager.getMessage("PLACE_ROCKET_FAIL"));
            event.getPlayer().getInventory().addItem(rocketManager.getSettings().getInventoryItem());
        }
        if(rocketPickups.remove(event.getPlayer()) != null) {
            event.getPlayer().sendMessage(rocketManager.getMessage("PICKUP_ROCKET_FAIL"));
        }
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        //If the player is not right clicking a block, return
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player player = event.getPlayer();
        boolean mainHand = false;
        if ((event.getHand() == EquipmentSlot.HAND && player.getInventory().getItemInMainHand().isSimilar(rocketManager.getSettings().getInventoryItem()))) {
            if(event.getBlockFace() != BlockFace.UP) {
                event.getPlayer().sendMessage(rocketManager.getMessage("ROCKET_PLACE_NOT_TOP"));
                return;
            }
            mainHand = true;

        } else if(event.getHand() == EquipmentSlot.OFF_HAND && player.getInventory().getItemInOffHand().isSimilar(rocketManager.getSettings().getInventoryItem())) {
            if(event.getBlockFace() != BlockFace.UP) {
                event.getPlayer().sendMessage(rocketManager.getMessage("ROCKET_PLACE_NOT_TOP"));
                return;
            }
        } else {
            return;
        }
        Location placementLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        if(!rocketManager.canLaunch(player, placementLocation))
            return;
        if(mainHand)
            player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        else
            player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
        rocketPlacements.put(player, new PendingRocketPlacement(placementLocation));
        player.sendMessage(rocketManager.getMessage("PLACE_ROCKET"));
    }
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
    private Map.Entry<Player, PendingRocketPickup> isBeingPickedUp(ArmorStand rocket) {
        for(Map.Entry<Player, PendingRocketPickup> pickup : rocketPickups.entrySet()) {
            if(pickup.getValue().getRocket().equals(rocket))
                return pickup;
        }
        return null;
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
        private final ArmorStand rocket;
        private final long time;

        public PendingRocketPickup(ArmorStand rocket) {
            this.rocket = rocket;
            this.time = System.currentTimeMillis() + (rocketManager.getSettings().getPickupTime()* 1000L);
        }

    }
    private void performLandingMechanics(Player player, RocketManager.TravelInfo travelInfo, float sideways, float forward) {
        RocketSettings settings = rocketManager.getSettings();
        ArmorStand stand = travelInfo.getStand();
        if(stand.getLocation().getY() <= settings.getVoidDetectionLayer()) {
            MortisRockets.getInstance().getManager().debug("Dropping because of void detection");
            Bukkit.getScheduler().runTask(rocketManager.getPlugin(), () -> {
                if(settings.isDropRocketOnLand())
                    player.getWorld().dropItem(stand.getLocation(), settings.getInventoryItem());
                stand.eject();
                stand.remove();
            });
            travelInfo.getRunningTask().cancel();
            rocketManager.getTraveling().remove(player.getUniqueId());
            return;
        }
        if(settings.isMidairCollision())
            Bukkit.getScheduler().runTask(rocketManager.getPlugin(), () -> {
                stand.getLocation().getNearbyEntitiesByType(LivingEntity.class, settings.getLandingDamageRadius()).forEach(x -> {
                    if(x == player || x == stand)
                        return;
                    x.damage(settings.getLandingDamage(), player);
                    Vector standVector = stand.getLocation().toVector();
                    Vector playerVector = x.getLocation().toVector();

// Calculate the direction vector from the stand to the player
                    Vector direction = playerVector.subtract(standVector).normalize();

// Apply a velocity in the opposite direction
                    Vector pushVelocity = direction.multiply(settings.getLandingPushbackStrength()); // Adjust the multiplier for push strength
                    x.setVelocity(x.getVelocity().add(pushVelocity));
                });
            });
        if (stand.isDead() || stand.isOnGround() || stand.isInLava() || stand.isInPowderedSnow() || stand.isInWaterOrBubbleColumn() /*|| stand.getPassengers().isEmpty()*/) {
            //Change this to make the armorstand persistant
            travelInfo.startDismount();
        } else {
            ArmorStand armorStand = rocketManager.getTraveling().get(player.getUniqueId()).getStand();
            if (armorStand != null) {
                Vector currentVelocity = armorStand.getVelocity();
                Vector direction = player.getLocation().getDirection();
                // Calculate sideways movement
                if (sideways != 0) {
                    Vector sidewaysVector = new Vector(direction.getZ(), 0, -direction.getX()).normalize().multiply(sideways * settings.getLandingMoveSpeed());
                    currentVelocity.add(sidewaysVector);
                }

                // Calculate forward movement
                if (forward != 0) {
                    Vector forwardVector = direction.clone().setY(0).normalize().multiply(forward * settings.getLandingMoveSpeed());
                    currentVelocity.add(forwardVector);
                }
                double maxVelocity = 0.75F; // Set the maximum velocity

                // Cap the velocity if it exceeds the maximum value
                if (currentVelocity.length() > maxVelocity) {
                    currentVelocity.normalize().multiply(maxVelocity);
                }
                // Restore the original Y velocity
                currentVelocity.setY(settings.getThrusterFallSpeed());
                // Apply the new velocity to the ArmorStand
                armorStand.setVelocity(currentVelocity);
            }

        }

    }
}
