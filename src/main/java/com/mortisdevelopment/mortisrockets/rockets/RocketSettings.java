package com.mortisdevelopment.mortisrockets.rockets;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@Getter
public class RocketSettings {

    private final String url;
    private final ItemStack launchItem;
    private final ItemStack landItem;
    private final ItemStack inventoryItem;
    private final int launchingTime;
    private final int launchingSpeed;
    private final int landingDistance;
    private final double landingParticleOffset;
    private final TownySettings townySettings;

    public RocketSettings(String url, ItemStack launchItem, ItemStack landItem, ItemStack inventoryItem, int launchingTime, int launchingSpeed, int landingDistance, double landingParticleOffset, ConfigurationSection townySettingsSection) {
        this.url = url;
        this.launchItem = launchItem;
        this.landItem = landItem;
        this.inventoryItem = inventoryItem;
        this.launchingTime = launchingTime;
        this.launchingSpeed = launchingSpeed;
        this.landingDistance = landingDistance;
        this.landingParticleOffset = landingParticleOffset;
        this.townySettings = loadTownySettings(townySettingsSection);
    }

    public boolean hasUrl() {
        return url != null;
    }

    public ArmorStand getRocket(Player player) {
        ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class);
        stand.getEquipment().setHelmet(launchItem, true);
        stand.addDisabledSlots(EquipmentSlot.HEAD);
        stand.setCanPickupItems(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCanMove(true);
        stand.setAI(true);
        stand.setInvisible(true);
        stand.addPassenger(player);
        return stand;
    }
    private RocketSettings.TownySettings loadTownySettings(ConfigurationSection section) {
        boolean useTowny = section.getBoolean("use-towny");
        boolean launchOwnTown = section.getBoolean("launch.own-town");
        boolean launchOwnNation = section.getBoolean("launch.own-nation");
        boolean launchAllyTerritory = section.getBoolean("launch.own-town");
        boolean launchEnemyTerritory = section.getBoolean("launch.own-town");
        boolean launchNeutralTerritory = section.getBoolean("");
        int launchDistanceFromUnauthorizedZones = section.getInt("launch.distance-from-unathorized-zones");
        boolean landOwnTown = section.getBoolean("land.own-town");
        boolean landOwnNation = section.getBoolean("land.own-nation");
        boolean landAllyTerritory = section.getBoolean("land.own-town");
        boolean landEnemyTerritory = section.getBoolean("land.own-town");
        boolean landNeutralTerritory = section.getBoolean("");
        int landDistanceFromUnauthorizedZones = section.getInt("land.distance-from-unathorized-zones");
        return new RocketSettings.TownySettings(useTowny, launchOwnTown, launchOwnNation, launchAllyTerritory, launchEnemyTerritory, launchNeutralTerritory, launchDistanceFromUnauthorizedZones, landOwnTown, landOwnNation, landAllyTerritory, landEnemyTerritory, landNeutralTerritory, landDistanceFromUnauthorizedZones);
    }
    @Getter
    public class TownySettings {
        private final boolean useTowny;
        private final boolean launchOwnTown;
        private final boolean launchOwnNation;
        private final boolean launchAllyTerritory;
        private final boolean launchEnemyTerritory;
        private final boolean launchNeutralTerritory;
        private final int launchDistanceFromUnauthorizedZones;
        private final boolean landOwnTown;
        private final boolean landOwnNation;
        private final boolean landAllyTerritory;
        private final boolean landEnemyTerritory;
        private final boolean landNeutralTerritory;
        private final int landDistanceFromUnauthorizedZones;
        public TownySettings(boolean useTowny, boolean launchOwnTown, boolean launchOwnNation, boolean launchAllyTerritory, boolean launchEnemyTerritory, boolean launchNeutralTerritory, int launchDistanceFromUnauthorizedZones, boolean landOwnTown, boolean landOwnNation, boolean landAllyTerritory, boolean landEnemyTerritory, boolean landNeutralTerritory, int landDistanceFromUnauthorizedZones) {
            this.useTowny = useTowny;
            this.launchOwnTown = launchOwnTown;
            this.launchOwnNation = launchOwnNation;
            this.launchAllyTerritory = launchAllyTerritory;
            this.launchEnemyTerritory = launchEnemyTerritory;
            this.launchNeutralTerritory = launchNeutralTerritory;
            this.launchDistanceFromUnauthorizedZones = launchDistanceFromUnauthorizedZones;
            this.landOwnTown = landOwnTown;
            this.landOwnNation = landOwnNation;
            this.landAllyTerritory = landAllyTerritory;
            this.landEnemyTerritory = landEnemyTerritory;
            this.landNeutralTerritory = landNeutralTerritory;
            this.landDistanceFromUnauthorizedZones = landDistanceFromUnauthorizedZones;
        }
    }
}
