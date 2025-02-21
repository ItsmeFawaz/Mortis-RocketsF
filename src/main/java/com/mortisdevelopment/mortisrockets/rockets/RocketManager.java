package com.mortisdevelopment.mortisrockets.rockets;

import com.mortisdevelopment.mortisrockets.MortisRockets;
import com.mortisdevelopment.mortisrockets.managers.CoreManager;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;

@Getter
public class RocketManager extends CoreManager {
    //TODO:-
    // - Handle disconnects, crashes, deaths
    private final MortisRockets plugin = MortisRockets.getInstance();
    private final RocketSettings settings;
    private final Map<String, Rocket> rocketById;
    private final Set<UUID> traveling;
    private final TownyAPI townyAPI = TownyAPI.getInstance();

    public RocketManager(RocketSettings settings) {
        this.settings = settings;
        this.rocketById = new HashMap<>();
        this.traveling = new HashSet<>();
        plugin.getServer().getPluginManager().registerEvents(new RocketListener(this), plugin);
    }

    public boolean canLand(Rocket rocket, Player player, Location location) {
        //TODO:-
        // - Prevent landing in liquids
        // - World whitelist/blacklist
        // - Specific rules of friend/enemy towns

        //If Towny is enabled
        if (!settings.getTownySettings().isUseTowny())
            return true;
        if (townyAPI.isWilderness(location))
            return isOutsideTownRadius(player, location, settings.getTownySettings().getLandDistanceFromUnauthorizedZones() * 16);
        else
            return getRespectiveTerritory(player, location).isLocationSafe(settings.getTownySettings());

    }

    public boolean canLaunch(Rocket rocket, Player player) {
        Location location = player.getLocation();
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
        if (!rocket.hasCost(player)) {
            player.sendMessage(getMessage("NOT_ENOUGH_MONEY"));
            return false;
        }
        return canLaunch(rocket, player);
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

    public boolean travel(Rocket rocket, Player player, RocketLocation rocketLocation) {
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
        rocket.removeCost(player);
        launch(rocket, player, location);
        return true;
    }

    public boolean travel(Rocket rocket, Player player) {
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
        rocket.removeCost(player);
        launch(rocket, player, location);
        return true;
    }

    //TODO:-
    // - Different models for launch and landing
    private void launch(Rocket rocket, Player player, Location location) {
        traveling.add(player.getUniqueId());
        player.sendMessage(rocket.getLaunchingMessage());
        ArmorStand stand = settings.getRocket(player);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                count++;
                stand.addPassenger(player);
                stand.getWorld().spawnParticle(Particle.LAVA, stand.getLocation(), 50);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
                if (count <= settings.getLaunchingTime()) {
                    stand.setVelocity(stand.getVelocity().setY(stand.getVelocity().getY() + settings.getLaunchingSpeed()));
                    return;
                }
                land(rocket, player, location, stand);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    //TODO :-
    // - Grace period after landing
    // - ability to use wasd to maneuver rocket on landing(possible use a different entity than armor stand?
    // - https://github.com/TrollsterCooleg/Parachute/blob/master/src/main/java/me/cooleg/parachute/Events/PlayerMove.java
    // - configurable landing particle offset(Y Axis)
    private void land(Rocket rocket, Player player, Location location, ArmorStand stand) {
        Location loc = new Location(location.getWorld(), location.getX(), location.getY() + settings.getLandingDistance(), location.getZ());
        stand.eject();
        stand.teleport(loc);
        stand.getEquipment().setHelmet(settings.getLandItem());
        player.teleport(loc);
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

    private enum TerritoryType {
        OWN_TOWN,
        OWN_NATION,
        ALLY_TERRITORY,
        ENEMY_TERRITORY,
        NEUTRAL_TERRITORY,
        WILDERNESS;

        boolean isLocationSafe(RocketSettings.TownySettings settings) {
            switch (this) {
                case OWN_TOWN:
                    return settings.isLandOwnTown();
                case OWN_NATION:
                    return settings.isLandOwnNation();
                case ALLY_TERRITORY:
                    return settings.isLandAllyTerritory();
                case ENEMY_TERRITORY:
                    return settings.isLandEnemyTerritory();
                case NEUTRAL_TERRITORY:
                    return settings.isLandNeutralTerritory();
                case WILDERNESS:
                    return true;
            }
            return false;
        }
    }
}
