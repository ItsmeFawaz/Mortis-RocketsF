package com.mortisdevelopment.mortisrockets.rockets;

import com.mortisdevelopment.mortisrockets.MortisRockets;
import com.mortisdevelopment.mortisrockets.managers.CoreManager;
import com.mortisdevelopment.mortisrockets.managers.FuelManager;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;

@Getter
public class RocketManager extends CoreManager {
    //TODO:-
    // - Handle disconnects, crashes, deaths
    private final MortisRockets plugin = MortisRockets.getInstance();
    private final FuelManager fuelManager;
    private final RocketSettings settings;
    private final Map<String, Rocket> rocketById;
    private final HashMap<ArmorStand, Long> placedRockets;
    private final HashMap<UUID, BukkitTask> launchTasks;
    private final Set<UUID> traveling;
    private final HashMap<UUID, LandingInfo> landing; //TODO: Possibly redundant, remove if not used(Possibly also use it for Allow Movement)
    private final TownyAPI townyAPI = TownyAPI.getInstance();

    public RocketManager(RocketSettings settings) {
        this.settings = settings;
        this.rocketById = new HashMap<>();
        this.traveling = new HashSet<>();
        this.landing = new HashMap<>();
        placedRockets = new HashMap<>();
        fuelManager = new FuelManager(this);
        launchTasks = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(new RocketListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(fuelManager, plugin);
    }

    public boolean canLand(Rocket rocket, Player player, Location location) {

        //If Towny is enabled
        if (!settings.getTownySettings().isUseTowny())
            return true;
        if (townyAPI.isWilderness(location))
            return isOutsideTownRadius(player, location, settings.getTownySettings().getLandDistanceFromUnauthorizedZones() * 16);
        else
            return getRespectiveTerritory(player, location).isLocationSafe(settings.getTownySettings());

    }

    public boolean canLaunch(Player player, Location location) {
        World world = location.getWorld();
        if (world.getHighestBlockAt(location).getLocation().getBlockY() > location.getBlockY()) {
            player.sendMessage(getMessage("NO_SPACE"));
            return false;
        }
        if (!settings.getTownySettings().isUseTowny())
            return true;
        if (townyAPI.isWilderness(location))
            return isOutsideTownRadius(player, location, settings.getTownySettings().getLandDistanceFromUnauthorizedZones() * 16);
        else
            return getRespectiveTerritory(player, location).isLocationSafe(settings.getTownySettings());

        /*if (!rocket.isOutsideTownRadius(location, rocket.getLaunchingRadius() * 16)) {
            player.sendMessage(getMessage("LAUNCH_NEAR_TOWN"));
            return false;
        }
        return true;*/
    }

    private boolean canTravel(Rocket rocket, Player player) {
        if (traveling.contains(player.getUniqueId())) {
            player.sendMessage(getMessage("ALREADY_TRAVELING"));
            return false;
        }
        return canLaunch(player, player.getLocation());
    }

    public void travel(Player player, Rocket rocket, String command) {
        DecimalFormat formatter = new DecimalFormat("#,###");
        Component message = getMessage("CONFIRMATION").replaceText(TextReplacementConfig.builder().match("%cost%").replacement(formatter.format(rocket.getCost())).build());
        player.sendMessage(message);
        Component accept = getMessage("CONFIRMATION_ACCEPT").clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command + " confirm"));
        player.sendMessage(accept);
        Component deny = getMessage("CONFIRMATION_DENY");
        player.sendMessage(deny);
    }

    public boolean travel(Rocket rocket, Player player, RocketLocation rocketLocation, boolean fromRocket) {
        if (!canTravel(rocket, player)) {
            return false;
        }
        Location location = rocketLocation.getLocation(rocket.getWorld().getWorld());
        if (location == null) {
            player.sendMessage(getMessage("NOT_SAFE"));
            return false;
        }
        if (rocket.isOutsideRange(rocketLocation)) {
            player.sendMessage(getMessage("OUTSIDE_RANGE"));
            return false;
        }
        if (!canLand(rocket, player, location)) {
            return false;
        }
        launch(rocket, player, location, fromRocket);
        return true;
    }

    public boolean travel(Rocket rocket, Player player, boolean fromRocket) {
        if (!canTravel(rocket, player)) {
            return false;
        }
        Location location = rocket.getLandingLocation();
        if (location == null) {
            player.sendMessage(getMessage("NOT_SAFE"));
            return false;
        }
        if (!canLand(rocket, player, location)) {
            return false;
        }
        launch(rocket, player, location, fromRocket);
        return true;
    }

    private void launch(Rocket rocket, Player player, Location location, boolean fromRocket) {
        traveling.add(player.getUniqueId());
        player.sendMessage(rocket.getLaunchingMessage());
        ArmorStand stand;
        if(!fromRocket) {
            stand = settings.getRocket(player);
            stand.addPassenger(player);
        } else {
            stand = (ArmorStand) player.getVehicle();
            placedRockets.remove(stand);
        }

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                count++;
                stand.getWorld().spawnParticle(Particle.LAVA, stand.getLocation(), 50);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                if (count <= settings.getLaunchingTime()) {
                    stand.setVelocity(stand.getVelocity().setY(stand.getVelocity().getY() + settings.getLaunchingSpeed()));
                    return;
                }
                land(rocket, player, location, stand);
                //landWithMovement(rocket, player, location, stand);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    //TODO :-
    // - Grace period after landing
    private void landWithMovement(Rocket rocket, Player player, Location location, ArmorStand stand) {
        Location loc = new Location(location.getWorld(), location.getX(), location.getY() + settings.getLandingDistance(), location.getZ());
        stand.remove();
        stand.teleport(loc);
        stand.getEquipment().setHelmet(settings.getLandItem());
        player.teleport(loc);
        ArmorStand landingStand = spawnRocket(loc, false);
        player.addPassenger(landingStand);
        LandingInfo landingInfo = new LandingInfo(rocket, player, landingStand, player.getLocation().toVector(), location);
        landing.put(player.getUniqueId(), landingInfo);
        //landingInfo.startSuicideBurn();
        new BukkitRunnable() {
            @Override
            public void run() {
                //player.addPassenger(landingStand);

                stopDropSpeed(player);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                player.setGravity(false);
                landingStand.getWorld().spawnParticle(Particle.LAVA, landingStand.getLocation().add(0, settings.getLandingParticleOffset(), 0), 50);
                if(isBlockBelow(player.getLocation())) {
                    performGracefulLand(player, rocket, landingStand);
                    cancel();
                    return;
                }
                /*if (player.isDead() || player.isOnGround() || player.isInLava() || player.isInPowderedSnow() || player.isInWaterOrBubbleColumn() || player.getPassengers().isEmpty()) {
                    traveling.remove(player.getUniqueId());
                    landing.remove(player.getUniqueId());
                    player.sendMessage(rocket.getLandingMessage());
                    player.setGravity(true);
                    player.eject();
                    landingStand.remove();
                    cancel();
                    return;
                }*/
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        //player.addPassenger(landingStand);
                        //stopDropSpeed(player);
                        player.setGravity(true);
                        landingStand.getWorld().spawnParticle(Particle.LAVA, landingStand.getLocation().add(0, settings.getLandingParticleOffset(), 0), 50);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }
    @Deprecated
    private void land(Rocket rocket, Player player, Location location, ArmorStand stand) {
        Location loc = new Location(location.getWorld(), location.getX(), location.getY() + settings.getLandingDistance(), location.getZ());
        stand.eject();
        stand.teleport(loc);
        player.teleport(loc);
        stand.addPassenger(player);
        landing.put(player.getUniqueId(), new LandingInfo(rocket, player, stand, player.getLocation().toVector(), location));
        new BukkitRunnable() {
            @Override
            public void run() {
                stand.addPassenger(player);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                stand.setGravity(false);
                stand.getWorld().spawnParticle(Particle.LAVA, stand.getLocation(), 50);
                if (stand.isDead() || stand.isOnGround() || stand.isInLava() || stand.isInPowderedSnow() || stand.isInWaterOrBubbleColumn() || stand.getPassengers().isEmpty()) {
                    traveling.remove(player.getUniqueId());
                    player.sendMessage(rocket.getLandingMessage());
                    stand.eject();
                    stand.remove();
                    landing.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        stand.addPassenger(player);
                        stand.setGravity(true);
                        stand.getWorld().spawnParticle(Particle.LAVA, stand.getLocation(), 50);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }
    public void launchOff(Player player, Rocket rocket, RocketLocation rocketLocation) {

        BukkitTask launchTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (rocketLocation != null) {
                    if(!travel(rocket, player, rocketLocation, true))
                        fuelManager.cancelFueling(player);
                } else {
                    if(!travel(rocket, player, true))
                        fuelManager.cancelFueling(player);
                }
                fuelManager.getFuelingPlayers().remove(player.getUniqueId());
                cancel();
            }
        }.runTaskLater(plugin,  settings.getLaunchLiftoffTime()*20L);
        launchTasks.put(player.getUniqueId(), launchTask);
    }
    public ArmorStand spawnRocket(Location location, boolean isLaunch) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.getEquipment().setHelmet(isLaunch ? settings.getLaunchItem() : settings.getLandItem(), true);
        stand.addDisabledSlots(EquipmentSlot.HEAD);
        stand.setCanPickupItems(false);
        stand.setSilent(true);
        //stand.setInvulnerable(true);
        stand.setCanMove(true);
        stand.setAI(true);
        stand.setInvisible(true);
        placedRockets.put(stand, System.currentTimeMillis() + (settings.getInactivityTime()*1000L));
        return stand;
    }
    private void performGracefulLand(Player player, Rocket rocket, ArmorStand landingStand) {
        player.setGravity(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, false ));
        Bukkit.getScheduler().runTaskTimer(plugin, x -> {
            if (player.isDead() || player.isOnGround() || player.isInLava() || player.isInPowderedSnow() || player.isInWaterOrBubbleColumn() || player.getPassengers().isEmpty()) {
                player.sendMessage(rocket.getLandingMessage());
                traveling.remove(player.getUniqueId());
                landing.remove(player.getUniqueId());
                player.eject();
                player.setFallDistance(0);
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                landingStand.remove();
                if(settings.isDropRocketOnLand())
                    player.getLocation().getWorld().dropItem(player.getLocation(), settings.getInventoryItem());
                x.cancel();
            }

        }, 5, 5);
    }
    private void performLanding(Player player, Rocket rocket, ArmorStand landingStand) {
        traveling.remove(player.getUniqueId());
        landing.remove(player.getUniqueId());
        player.sendMessage(rocket.getLandingMessage());
        player.setGravity(true);
        player.eject();
        player.setFallDistance(0);
        landingStand.remove();
        if(settings.isDropRocketOnLand())
            player.getLocation().getWorld().dropItem(player.getLocation(), settings.getInventoryItem());
    }
    private TerritoryType getRespectiveTerritory(Player player, Location location) {
        Resident resident = townyAPI.getResident(player);
        Town landTown;
        //If landing location is in own town
        if ((landTown = townyAPI.getTown(location)).hasResident(player))
            return TerritoryType.OWN_TOWN;

        Town residentTown = resident.getTownOrNull();
        //If resident doesn't have a town, it's neutral territory
        if (residentTown == null)
            return TerritoryType.NEUTRAL_TERRITORY;
        Nation landNation;

        if (landTown.hasNation() && (landNation = landTown.getNationOrNull()) != null && landNation.hasResident(resident))
            return TerritoryType.OWN_NATION;
        if (landTown.hasAlly(residentTown))
            return TerritoryType.ALLY_TERRITORY;
        if (landTown.hasEnemy(residentTown))
            return TerritoryType.ENEMY_TERRITORY;
        return TerritoryType.NEUTRAL_TERRITORY;
    }

    public boolean isOutsideTownRadius(Player player, Location location, int radius) {
        if (!plugin.isTowny() || radius <= 0) {
            return true;
        }
        TownyAPI towny = TownyAPI.getInstance();
        double locationX = location.getX();
        double locationY = location.getY();
        double locationZ = location.getZ();
        for (double x = -radius; x <= radius; x++) {
            for (double z = -radius; z <= radius; z++) {
                Location loc = new Location(location.getWorld(), locationX + x, locationY, locationZ + z);
                if (!towny.isWilderness(loc) && !getRespectiveTerritory(player, location).isLocationSafe(settings.getTownySettings())) {
                    return false;
                }
            }
        }
        return true;
    }
    public boolean isRocket(Entity entity) {
        if(entity instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) entity;
            return stand.getEquipment().getHelmet().isSimilar(settings.getLaunchItem()) || stand.getEquipment().getHelmet().isSimilar(settings.getLandItem());
        }
        return false;
    }
    private boolean isBlockBelow(Location location) {
        boolean blockBelow = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (location.add(x, y ,z).getBlock().getType() == Material.AIR) {continue;}
                    blockBelow = true;
                    break;
                }
                if (blockBelow) {break;}
            }
            if (blockBelow) {break;}
        }
        return blockBelow;
    }
    private void stopDropSpeed(Player player) {
        Vector vector = player.getVelocity();
        vector.setY(0.01F);
        player.setVelocity(vector);
    }
    private enum TerritoryType {
        OWN_TOWN,
        OWN_NATION,
        ALLY_TERRITORY,
        ENEMY_TERRITORY,
        NEUTRAL_TERRITORY,
        WILDERNESS;

        boolean isLocationSafe(RocketSettings.TownySettings settings) {
            return switch (this) {
                case OWN_TOWN -> settings.isLandOwnTown();
                case OWN_NATION -> settings.isLandOwnNation();
                case ALLY_TERRITORY -> settings.isLandAllyTerritory();
                case ENEMY_TERRITORY -> settings.isLandEnemyTerritory();
                case NEUTRAL_TERRITORY -> settings.isLandNeutralTerritory();
                case WILDERNESS -> true;
            };
        }
    }
    @Getter
    public class LandingInfo {
        private final Rocket rocket;
        private final Player player;
        private final ArmorStand landingStand;
        @Setter
        private Vector pastPoint;
        private BukkitTask landingTask;
        private final Location landingLocation;

        public LandingInfo(Rocket rocket, Player player, ArmorStand landingStand, Vector pastPoint, Location landingLocation) {
            this.rocket = rocket;
            this.player = player;
            this.landingStand = landingStand;
            this.pastPoint = pastPoint;
            this.landingLocation = landingLocation;
        }
        public void startSuicideBurn() {
            landingTask = new BukkitRunnable() {
                @Override
                public void run() {
                    landingStand.getWorld().spawnParticle(Particle.LAVA, landingStand.getLocation().add(0, settings.getLandingParticleOffset(), 0), 50);
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }
}
