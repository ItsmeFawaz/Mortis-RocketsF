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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;
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
    private final BukkitTask timeScheduler;
    private final ProtocolManager manager = ProtocolLibrary.getProtocolManager();

    public RocketListener(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        rocketPlacements = new HashMap<>();
        rocketPickups = new HashMap<>();

        manager.addPacketListener(new PacketAdapter(MortisRockets.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!(rocketManager.getLanding().containsKey(event.getPlayer().getUniqueId()))) {
                    return;
                }
                Player player = event.getPlayer();
                PacketContainer packet = event.getPacket();
                float sideways = packet.getFloat().read(0);
                float forward = packet.getFloat().read(1);
                float multiplier = 0.1f; // Adjust this value as needed

                // Apply the multiplier to the sideways and forward values
                sideways *= multiplier;
                forward *= multiplier;
                // Get the player's direction vector
                Vector direction = player.getLocation().getDirection();
                // Calculate the sideways movement vector
                Vector sidewaysVector = new Vector(direction.getZ(), 0, direction.getX()).normalize().multiply(sideways);

                // Calculate the forward movement vector
                Vector forwardVector = direction.clone().setY(0).normalize().multiply(forward);

                // Combine the vectors to get the final velocity
                Vector velocity = forwardVector.add(sidewaysVector);

                // Set the velocity of the ArmorStand
                ArmorStand armorStand = rocketManager.getLanding().get(player.getUniqueId()).getLandingStand(); // Replace with your method to get the ArmorStand
                if (armorStand != null) {
                    Vector currentVelocity = armorStand.getVelocity();
                    //Bukkit.broadcastMessage("X: " + currentVelocity.getX() + " Y: " + currentVelocity.getY() + " Z: " + currentVelocity.getZ());
                    if(currentVelocity.getX() > 0.5F) {
                        currentVelocity.setX(0.5F);
                    }
                    if(currentVelocity.getZ() > 0.5F) {
                        currentVelocity.setZ(0.5F);
                    }
                    if(currentVelocity.getX() < -0.5F)
                        currentVelocity.setX(-0.5F);
                    if(currentVelocity.getZ() < -0.5F)
                        currentVelocity.setZ(-0.5F);
                    // Set the new velocity while keeping the Y component unchanged
                    armorStand.setVelocity(currentVelocity.add(velocity));
                }
            }
        });
        timeScheduler = Bukkit.getScheduler().runTaskTimer(rocketManager.getPlugin(), () -> {
            //TODO: - Take the grace period time from config
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

        if (!(e.getEntity() instanceof Player player)) {
            return;
        }
        if(e.getDismounted() instanceof ArmorStand rocket && rocketManager.getPlacedRockets().containsKey(rocket) && rocketManager.getPlacedRockets().get(rocket) == -1) {
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
        if(rocketManager.getSettings().isLaunchInvincibility() && rocketManager.getLaunchTasks().containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (!rocketManager.getTraveling().contains(player.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if(rocketManager.getLanding().containsKey(event.getPlayer().getUniqueId())) {
            performLandingMechanics(event.getPlayer(), rocketManager.getLanding().get(event.getPlayer().getUniqueId()));
        }
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
        Location placementLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        if(!rocketManager.canLaunch(player, placementLocation))
            return;
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
        private final ArmorStand rocket;
        private final long time;

        public PendingRocketPickup(ArmorStand rocket) {
            this.rocket = rocket;
            this.time = System.currentTimeMillis() + (rocketManager.getSettings().getPickupTime()* 1000L);
        }

    }
    private void performLandingMechanics(Player player, RocketManager.LandingInfo landingInfo) {
        RocketSettings settings = rocketManager.getSettings();
        if (player.isDead() || player.isOnGround() || player.isInLava() || player.isInPowderedSnow() || player.isInWaterOrBubbleColumn() || player.getPassengers().isEmpty()) {
            player.sendMessage(landingInfo.getRocket().getLandingMessage());
            rocketManager.getTraveling().remove(player.getUniqueId());
            rocketManager.getLanding().remove(player.getUniqueId());
            player.eject();
            player.setFallDistance(0);
            landingInfo.getLandingStand().remove();
            landingInfo.getLandingTask().cancel();
            if(settings.isDropRocketOnLand())
                player.getLocation().getWorld().dropItem(player.getLocation(), settings.getInventoryItem());
        } else {
            if((player.getLocation().getY() - landingInfo.getLandingLocation().getY()) > 20) {
                if(!settings.isLandingAllowMovement()) {
                    player.setVelocity(new Vector(0, player.getVelocity().getY(), 0));
                }
            } else {
                final Vector vector = player.getLocation().toVector().subtract(landingInfo.getPastPoint()).multiply(.8 * (rocketManager.getSettings().isLandingAllowMovement() ? rocketManager.getSettings().getLandingMoveSpeed() : 0));
                player.setVelocity(vector.setY(-.3));
                landingInfo.setPastPoint(player.getLocation().toVector());
            }

            /*;*/

        }

    }
}
