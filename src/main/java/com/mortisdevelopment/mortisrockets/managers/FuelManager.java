package com.mortisdevelopment.mortisrockets.managers;

import com.mortisdevelopment.mortisrockets.MortisRockets;
import com.mortisdevelopment.mortisrockets.rockets.Rocket;
import com.mortisdevelopment.mortisrockets.rockets.RocketLocation;
import com.mortisdevelopment.mortisrockets.rockets.RocketManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.text.DecimalFormat;
import java.util.*;

public class FuelManager implements Listener {
    private final MortisRockets plugin = MortisRockets.getInstance();
    private final RocketManager rocketManager;
    private final DecimalFormat formatter = new DecimalFormat("#,###");
    private HashMap<Material, Integer> fuelMap = new HashMap<>();
    private HashMap<UUID, FuelProgress> fuelingPlayers;
    private HashMap<UUID, Fuel> fueling;


    public FuelManager(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        fuelingPlayers = new HashMap<>();
        fueling = new HashMap<>();
    }

    public void startFueling(Player player, Rocket rocket) {
        fuelingPlayers.put(player.getUniqueId(), new FuelProgress(rocket));
        player.sendMessage(rocketManager.getMessage("CONFIRMATION").replaceText(TextReplacementConfig.builder().match("%cost%").replacement(formatter.format(rocket.getCost())).build()));
    }
    public void startFueling(Player player, Rocket rocket, RocketLocation rocketLocation) {
        fuelingPlayers.put(player.getUniqueId(), new FuelProgress(rocket, rocketLocation));
        player.sendMessage(rocketManager.getMessage("CONFIRMATION").replaceText(TextReplacementConfig.builder().match("%cost%").replacement(formatter.format(rocket.getCost())).build()));
    }
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if(!fuelingPlayers.containsKey(event.getPlayer().getUniqueId()))
            return;
        if(event.getAction() != Action.RIGHT_CLICK_AIR)
            return;
        if(!(event.getHand() == EquipmentSlot.HAND && isFuel(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand())))
            return;

        if(rocketManager.getSettings().isInsertFuelIndividually()) {

            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            //If amount of fuel in hand is more than required
            item.setAmount(item.getAmount() - 1);
            ItemStack usedFuel = item.clone();
            usedFuel.setAmount(1);
            startFuelingTimer(event.getPlayer(), usedFuel);
        } else {
            FuelProgress fuelProgress = fuelingPlayers.get(event.getPlayer().getUniqueId());
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            int fuelAmount = fuelMap.get(item.getType());
            //If amount of fuel in hand is more than required

            if(fuelAmount*item.getAmount() >= fuelProgress.getRemainingCost()) {
                int usedAmount = (int) fuelProgress.getRemainingCost()/fuelAmount;
                item.setAmount(item.getAmount() - usedAmount);
                ItemStack usedFuel = item.clone();
                startFuelingTimer(event.getPlayer(), usedFuel);
            } else{
                event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                startFuelingTimer(event.getPlayer(), item);

            }
        }

    }
    //TODO: Handle dismount, disconnects, crashes, deaths etc
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && fuelingPlayers.containsKey(player.getUniqueId())) {
            FuelProgress progress = fuelingPlayers.remove(player.getUniqueId());
            progress.getUsedFuel().forEach(fuel -> player.getInventory().addItem(fuel));
        }
    }
    private void startFuelingTimer(Player player, ItemStack stack) {
        player.sendMessage(rocketManager.getMessage("FUELING_ROCKET"));
        int[] counter = {1};
        BukkitTask fuelingTask = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + getCounterMessage(counter[0])));
                counter[0]++;
                if (counter[0] > rocketManager.getSettings().getFuelTime()) {
                    FuelProgress fuelProgress = fuelingPlayers.get(player.getUniqueId());
                    int fuelAmount = fuelMap.get(stack.getType()) * stack.getAmount();
                    //If amount of fuel in hand is more than required
                    fuelProgress.getUsedFuel().add(stack);
                    fuelProgress.setRemainingCost(fuelProgress.getRemainingCost() - fuelAmount);

                    //TODO: Add launch-off timer!
                    if (fuelProgress.getRemainingCost() <= 0) {
                        player.sendMessage(rocketManager.getMessage("FUELING_COMPLETE"));
                        if (fuelProgress.getRocketLocation() != null)
                            rocketManager.travel(fuelProgress.getRocket(), player, fuelProgress.getRocketLocation(), true);
                        else
                            rocketManager.travel(fuelProgress.getRocket(), player, true);
                        fuelingPlayers.remove(player.getUniqueId());
                    } else {
                        player.sendMessage(rocketManager.getMessage("ADDED_FUEL").replaceText(TextReplacementConfig.builder().match("%fuel%").replacement(formatter.format(fuelProgress.getRemainingCost())).build()));
                    }
                    fueling.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 20);
        fueling.put(player.getUniqueId(), new Fuel(stack, fuelingTask));
    }
    private void cancelFueling(Player player) {
    }
    private String getCounterMessage(int count) {
        String message = "";
        for(int i = 0; i < count; i++) {
            message += ".";
        }
        return message;
    }
    private boolean isFuel(Player player, ItemStack stack) {
        if(stack != null && fuelMap.containsKey(stack.getType())) {
            return true;
        } else {
            player.sendMessage(rocketManager.getMessage("INVALID_FUEL"));
            return false;
        }
    }
    public void loadFuelItems(ConfigurationSection section) {
        section.getValues(false).forEach((key, value) -> {;
            fuelMap.put(Material.matchMaterial( key.toUpperCase()), (Integer) value);
        });
    }
    @AllArgsConstructor
    public class Fuel {
        private final ItemStack stack;
        private final BukkitTask task;
    }
    @Getter
    public class FuelProgress {
        private final HashSet<ItemStack> usedFuel;
        @Setter
        private double remainingCost;
        private final Rocket rocket;
        private final RocketLocation rocketLocation;
        private FuelProgress(Rocket rocket) {
            this.rocket = rocket;
            this.rocketLocation = null;
            this.usedFuel = new HashSet<>();
            this.remainingCost = rocket.getCost();
        }
        private FuelProgress(Rocket rocket, RocketLocation rocketLocation) {
            this.rocket = rocket;
            this.rocketLocation = rocketLocation;
            this.usedFuel = new HashSet<>();
            this.remainingCost = rocket.getCost();
        }
    }
}
