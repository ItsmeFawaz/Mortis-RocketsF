package com.mortisdevelopment.mortisrockets.config;

import com.mortisdevelopment.mortisrockets.rockets.*;
import com.mortisdevelopment.mortisrockets.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.stream.Collectors;

public class MainConfig extends Config {

    private final ConfigManager configManager;

    public MainConfig(ConfigManager configManager) {
        super("config.yml");
        this.configManager = configManager;
        loadConfig();
    }

    @Override
    public void loadConfig() {
        File file = saveConfig();
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        RocketSettings settings = getSettings(config.getConfigurationSection("settings"));
        if (settings == null) {
            return;
        }
        configManager.getManager().setRocketManager(new RocketManager(settings));
        loadRockets(config.getConfigurationSection("rockets"));
        configManager.getManager().getRocketManager().getFuelManager().loadFuelItems(config.getConfigurationSection("settings.launch.fuel"));
        configManager.getManager().getRocketManager().loadMessages(config.getConfigurationSection("messages"));
    }

    private RocketSettings getSettings(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String url = section.getString("url");
        ItemStack launchItem = getItem(section.getConfigurationSection("item.liftoff"));
        ItemStack landingItem = getItem(section.getConfigurationSection("item.landing"));
        ItemStack inventoryItem = getItem(section.getConfigurationSection("item.inventory"));
        int launchingTime = section.getInt("launch.time");
        int launchingSpeed = section.getInt("launch.speed");
        int landingDistance = section.getInt("land.distance");
        double landingParticleOffset = section.getDouble("item.landing.landing-particle-offset");

        return new RocketSettings(url,
                launchItem,
                landingItem,
                inventoryItem,
                section.getInt("deployment.place-time"),
                section.getInt("deployment.pickup-time"),
                section.getInt("deployment.fuel-time"),
                launchingTime,
                section.getInt("launch.liftoff-time"),
                launchingSpeed,
                section.getBoolean("launch.launch-invincibility"),
                section.getBoolean("launch.require-fuel"),
                section.getBoolean("launch.insert-fuel-individually"),
                landingDistance,
                section.getBoolean("land.allow-movement"),
                section.getDouble("land.movement-speed"),
                section.getBoolean("land.drop-rocket-on-landing"),
                section.getInt("land.landing-dismount-time"),
                section.getBoolean("land.protect-while-dismounting"),
                landingParticleOffset,
                section.getInt("launch.inactivity-timer"),
                section.getConfigurationSection("towny-integration"));
    }
    private ItemStack getItem(ConfigurationSection itemSection) {
        Material material;
        try {
            material = Material.valueOf(itemSection.getString("material"));
        }catch (IllegalArgumentException | NullPointerException exp) {
            return null;
        }
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if(itemSection.contains("name")) {
            meta.setDisplayName(MessageUtils.color(itemSection.getString("name")));
        }
        if(itemSection.contains("description")) {
            meta.setLore(itemSection.getStringList("description").stream().map(MessageUtils::color).collect(Collectors.toList()));
        }
        meta.setCustomModelData(itemSection.getInt("custom-model-data"));
        item.setItemMeta(meta);
        return item;
    }

    private void loadRockets(ConfigurationSection rockets) {
        if (rockets == null) {
            return;
        }
        for (String id : rockets.getKeys(false)) {
            ConfigurationSection section = rockets.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String rawWorld = section.getString("world");
            if (rawWorld == null) {
                continue;
            }
            RocketWorld world = new RocketWorld(rawWorld);
            String rawLoc = section.getString("location");
            String rawLoc2 = section.getString("location2");
            if (rawLoc == null || rawLoc2 == null) {
                continue;
            }
            String[] rawLocation = rawLoc.split(",");
            String[] rawLocation2 = rawLoc2.split(",");
            if (rawLocation.length < 2 || rawLocation2.length < 2) {
                continue;
            }
            double x1;
            double z1;
            double x2;
            double z2;
            try {
                x1 = Double.parseDouble(rawLocation[0]);
                z1 = Double.parseDouble(rawLocation[1]);
                x2 = Double.parseDouble(rawLocation2[0]);
                z2 = Double.parseDouble(rawLocation2[1]);
            }catch (NumberFormatException | NullPointerException exp) {
                continue;
            }
            RocketLocation location = new RocketLocation(x1, z1);
            RocketLocation location2 = new RocketLocation(x2, z2);
            boolean specificLocation = section.getBoolean("specific-location");
            double cost = section.getDouble("cost");
            int launchingRadius = section.getInt("launching-radius");
            int landingRadius = section.getInt("landing-radius");
            boolean oceanChecker = section.getBoolean("ocean-checker");
            String launchingMessage = MessageUtils.color(section.getString("launching-message"));
            String landingMessage = MessageUtils.color(section.getString("landing-message"));
            Rocket rocket = new Rocket(id, world, location, location2, specificLocation, cost, launchingRadius, landingRadius, oceanChecker, launchingMessage, landingMessage);
            configManager.getManager().getRocketManager().getRocketById().put(id, rocket);
        }
    }
}
