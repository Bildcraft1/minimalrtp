package org.whixard.minimalrtp;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Config implements ConfigurationSerializable {
    private String world;
    private int minDistance;
    private int maxDistance;
    private int maxAttempts;
    private int cooldownSeconds;
    private String cooldownMessage;
    private String okMessage;
    private String errorMessage;

    public Config(String world, int minDistance, int maxDistance, int maxAttempts, int cooldownSeconds, String cooldownMessage, String okMessage, String errorMessage) {
        this.world = world;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.maxAttempts = maxAttempts;
        this.cooldownSeconds = cooldownSeconds;
        this.cooldownMessage = cooldownMessage;
        this.okMessage = okMessage;
        this.errorMessage = errorMessage;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("world", world);
        data.put("minDistance", minDistance);
        data.put("maxDistance", maxDistance);
        data.put("maxAttempts", maxAttempts);
        data.put("cooldownSeconds", cooldownSeconds);
        data.put("cooldownMessage", cooldownMessage);
        data.put("okMessage", okMessage);
        data.put("errorMessage", errorMessage);
        return data;
    }

    public static Config deserialize(Map<String, Object> map) {
        String world = (String) map.get("world");
        int minDistance = (int) map.get("minDistance");
        int maxDistance = (int) map.get("maxDistance");
        int maxAttempts = (int) map.get("maxAttempts");
        int cooldownSeconds = (int) map.get("cooldownSeconds");
        String cooldownMessage = (String) map.get("cooldownMessage");
        String okMessage = (String) map.get("okMessage");
        String errorMessage = (String) map.get("errorMessage");

        return new Config(world, minDistance, maxDistance, maxAttempts, cooldownSeconds, cooldownMessage, okMessage, errorMessage);
    }
}
