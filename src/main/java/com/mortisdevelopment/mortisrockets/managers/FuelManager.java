package com.mortisdevelopment.mortisrockets.managers;

import com.mortisdevelopment.mortisrockets.MortisRockets;
import com.mortisdevelopment.mortisrockets.rockets.Rocket;
import com.mortisdevelopment.mortisrockets.rockets.RocketLocation;
import com.mortisdevelopment.mortisrockets.rockets.RocketManager;
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
import org.spigotmc.event.entity.EntityDismountEvent;

import java.text.DecimalFormat;
import java.util.*;

public class FuelManager implements Listener {
    private final MortisRockets plugin = MortisRockets.getInstance();
    private final RocketManager rocketManager;
    private HashMap<Material, Integer> fuelMap = new HashMap<>();
    private HashMap<UUID, FuelProgress> fuelingPlayers;
    private HashMap<UUID, ItemStack> fueling;

    public FuelManager(RocketManager rocketManager) {
        this.rocketManager = rocketManager;
        fuelingPlayers = new HashMap<>();
        fueling = new HashMap<>();
    }

    public void startFueling(Player player, Rocket rocket) {
        DecimalFormat formatter = new DecimalFormat("#,###");
        fuelingPlayers.put(player.getUniqueId(), new FuelProgress(rocket));
        player.sendMessage(rocketManager.getMessage("CONFIRMATION").replaceText(TextReplacementConfig.builder().match("%cost%").replacement(formatter.format(rocket.getCost())).build()));
    }
    public void startFueling(Player player, Rocket rocket, RocketLocation rocketLocation) {
        DecimalFormat formatter = new DecimalFormat("#,###");
        fuelingPlayers.put(player.getUniqueId(), new FuelProgress(rocket, rocketLocation));
        player.sendMessage(rocketManager.getMessage("CONFIRMATION").replaceText(TextReplacementConfig.builder().match("%cost%").replacement(formatter.format(rocket.getCost())).build()));
    }
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if(!fuelingPlayers.containsKey(event.getPlayer().getUniqueId()))
            return;
        if(event.getAction() != Action.RIGHT_CLICK_AIR)
            return;
        if(!(event.getHand() == EquipmentSlot.HAND && isFuel(event.getPlayer().getInventory().getItemInMainHand())))
            return;

        if(rocketManager.getSettings().isInsertFuelIndividually()) {

            //TODO: Set fueling timer
            FuelProgress fuelProgress = fuelingPlayers.get(event.getPlayer().getUniqueId());
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            int fuelAmount = fuelMap.get(item.getType());
            //If amount of fuel in hand is more than required
            item.setAmount(item.getAmount() - 1);
            ItemStack usedFuel = item.clone();
            usedFuel.setAmount(1);
            fuelProgress.getUsedFuel().add(usedFuel);
            fuelProgress.setRemainingCost(fuelProgress.getRemainingCost() - fuelAmount);
            if(fuelProgress.getRemainingCost() <= 0) {
                if(fuelProgress.getRocketLocation() != null)
                    rocketManager.travel(fuelProgress.getRocket(), event.getPlayer(), fuelProgress.getRocketLocation(), true);
                else
                    rocketManager.travel(fuelProgress.getRocket(), event.getPlayer(), true);
            }
        } else {
            FuelProgress fuelProgress = fuelingPlayers.get(event.getPlayer().getUniqueId());
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            int fuelAmount = fuelMap.get(item.getType());
            //If amount of fuel in hand is more than required
            //TODO: Set fueling timer
            if(fuelAmount*item.getAmount() >= fuelProgress.getRemainingCost()) {
                int usedAmount = (int) fuelProgress.getRemainingCost()/fuelAmount;
                item.setAmount(item.getAmount() - usedAmount);
                ItemStack usedFuel = item.clone();
                usedFuel.setAmount(usedAmount);
                fuelProgress.getUsedFuel().add(usedFuel);
                if(fuelProgress.getRocketLocation() != null)
                    rocketManager.travel(fuelProgress.getRocket(), event.getPlayer(), fuelProgress.getRocketLocation(), true);
                else
                    rocketManager.travel(fuelProgress.getRocket(), event.getPlayer(), true);
                //TODO: Launch rocket
            } else{
                fuelProgress.getUsedFuel().add(item);
                fuelProgress.setRemainingCost(fuelProgress.getRemainingCost() - fuelAmount*item.getAmount());
                event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            event.getPlayer().getInventory().getItemInMainHand().setAmount(event.getPlayer().getInventory().getItemInMainHand().getAmount() - 1);
            fuelingPlayers.remove(event.getPlayer().getUniqueId());
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
    private boolean isFuel(ItemStack stack) {
        return stack != null && fuelMap.containsKey(stack.getType());
    }
    public void loadFuelItems(ConfigurationSection section) {
        section.getValues(false).forEach((key, value) -> {;
            fuelMap.put(Material.matchMaterial( key.toUpperCase()), (Integer) value);
        });
    }
    private void startFueling(Player player, ItemStack stack) {
        fueling.put(player.getUniqueId(), stack);
        int[] counter = {1};
        Bukkit.getScheduler().runTaskTimer(plugin, (x) -> {
            player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + getCounterMessage(counter[0])));
            counter[0]++;
            if(counter[0] > rocketManager.getSettings().getFuelTime()) {
                FuelProgress fuelProgress = fuelingPlayers.get(player.getUniqueId());
                int fuelAmount = fuelMap.get(stack.getType())*stack.getAmount();
                //If amount of fuel in hand is more than required
                fuelProgress.getUsedFuel().add(stack);
                fuelProgress.setRemainingCost(fuelProgress.getRemainingCost() - fuelAmount);
                if(fuelProgress.getRemainingCost() <= 0) {
                    if(fuelProgress.getRocketLocation() != null)
                        rocketManager.travel(fuelProgress.getRocket(), player, fuelProgress.getRocketLocation(), true);
                    else
                        rocketManager.travel(fuelProgress.getRocket(), player, true);
                }
                fueling.remove(player.getUniqueId());
                x.cancel();
            }
        }, 0, 20);

    }
    private String getCounterMessage(int count) {
        String message = "";
        for(int i = 0; i < count; i++) {
            message += ".";
        }
        return message;
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
