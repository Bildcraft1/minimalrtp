package org.whixard.minimalrtp;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import org.eclipse.sisu.bean.LifecycleManager;
import org.whixard.minimalrtp.commands.RTP;

public final class MinimalRTP extends JavaPlugin {
    public static MinimalRTP instance = null;
    @Override
    public void onEnable() {
        instance = this;

        // save default config
        saveResource("config.yml", false);
        ConfigurationSerialization.registerClass(Config.class);

        // register commands
        final BasicCommand rtpCommand = new RTP();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register("rtp", rtpCommand);
            commands.registrar().register("minimalrtp", rtpCommand);
        });
        getLogger().info("MinimalRTP enabled!");
        getLogger().info("Worldguard Integration status: " + getConfig().getBoolean("enableWorldGuard"));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
