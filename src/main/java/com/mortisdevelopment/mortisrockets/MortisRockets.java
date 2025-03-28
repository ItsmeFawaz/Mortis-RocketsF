package com.mortisdevelopment.mortisrockets;

import com.mortisdevelopment.mortisrockets.managers.Manager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class MortisRockets extends JavaPlugin {

    @Getter
    private static MortisRockets Instance;
    @Getter
    public boolean towny;
    @Getter
    private Manager manager;


    @Override
    public void onEnable() {
        // Plugin startup logic
        Instance = this;
        towny = getServer().getPluginManager().getPlugin("Towny") != null;
        manager = new Manager();
    }

}
