package org.whixard.minimalrtp.commands;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.whixard.minimalrtp.MinimalRTP;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RTP implements BasicCommand {
    // HashMap to store player UUID and last command usage time
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    // HashMap to track players currently searching for a location
    private final HashMap<UUID, Boolean> searching = new HashMap<>();

    @Override
    public void execute(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] strings) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            return;
        }

        // Check if player is already searching
        if (searching.getOrDefault(player.getUniqueId(), false)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>You are already searching for a random location. Please wait."));
            return;
        }

        // Check if player is on cooldown
        if (isOnCooldown(player)) {
            long timeLeft = getRemainingCooldown(player);
            String cooldownMessage = MinimalRTP.instance.getConfig().getString("cooldownMessage", "<red>You must wait %time% seconds before using this command again.");
            cooldownMessage = cooldownMessage.replace("%time%", String.valueOf(timeLeft));
            player.sendMessage(MiniMessage.miniMessage().deserialize(cooldownMessage));
            return;
        }

        // Find a random location in the world
        if (MinimalRTP.instance.getConfig().getString("world") == null) {
            MinimalRTP.instance.getLogger().severe("World name is null, please check your config.yml");
            return;
        }

        World world = Bukkit.getWorld(MinimalRTP.instance.getConfig().getString("world"));
        if (world == null) {
            MinimalRTP.instance.getLogger().severe("World not found: " + MinimalRTP.instance.getConfig().getString("world"));
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>World not found. Please contact an administrator."));
            return;
        }

        // Mark player as searching
        searching.put(player.getUniqueId(), true);

        // Send searching message
        player.sendMessage(MiniMessage.miniMessage().deserialize(MinimalRTP.instance.getConfig().getString("searchingMessage", "<yellow>Searching for a safe location...")));

        // Run the location search asynchronously
        findSafeLocationAsync(world, player).thenAccept(location -> {
            // This runs when the async task completes
            if (location != null) {
                // Use the location - teleport must be run on the main thread
                Bukkit.getScheduler().runTask(MinimalRTP.instance, () -> {
                    // Check if player is still online
                    if (!player.isOnline()) {
                        searching.remove(player.getUniqueId());
                        return;
                    }

                    Location teleportLocation = new Location(world, location[0], location[1], location[2]);
                    player.teleport(teleportLocation);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(MinimalRTP.instance.getConfig().getString("okMessage")));

                    // Set cooldown for the player
                    setCooldown(player);

                    // Mark player as no longer searching
                    searching.remove(player.getUniqueId());
                });
            } else {
                // No safe location found - must run on main thread
                Bukkit.getScheduler().runTask(MinimalRTP.instance, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(MinimalRTP.instance.getConfig().getString("errorMessage")));
                    }
                    MinimalRTP.instance.getLogger().warning("No safe location found for player " + player.getName());

                    // Mark player as no longer searching
                    searching.remove(player.getUniqueId());
                });
            }
        });
    }

    @Override
    public @Nullable String permission() {
        return "minimalrtp.command.rtp";
    }

    /**
     * Asynchronously find a safe location
     * @param world The world to search in
     * @param player The player who is searching
     * @return CompletableFuture with the location coordinates or null if none found
     */
    private CompletableFuture<int[]> findSafeLocationAsync(World world, Player player) {
        CompletableFuture<int[]> future = new CompletableFuture<>();

        // Run the search in a separate thread
        new BukkitRunnable() {
            @Override
            public void run() {
                int maxAttempts = MinimalRTP.instance.getConfig().getInt("maxAttempts");
                int minDistance = MinimalRTP.instance.getConfig().getInt("minDistance");
                int maxDistance = MinimalRTP.instance.getConfig().getInt("maxDistance");

                int[] location = generateRandomLocation(world, minDistance, maxDistance);;
                for (int i = 0; i < maxAttempts; i++) {
                    location = generateRandomLocation(world, minDistance, maxDistance);
                    if (location != null) {
                        break;
                    }

                    // Send progress message every 10 attempts
                    if ((i + 1) % 10 == 0 && player.isOnline()) {
                        final int attempt = i + 1;
                        Bukkit.getScheduler().runTask(MinimalRTP.instance, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<yellow>Still searching... Attempt " + attempt + "/" + maxAttempts));
                            }
                        });
                    }
                }

                future.complete(location);
            }
        }.runTaskAsynchronously(MinimalRTP.instance);

        return future;
    }

    /**
     * Set cooldown for a player
     * @param player The player to set cooldown for
     */
    private void setCooldown(Player player) {
        int cooldownSeconds = MinimalRTP.instance.getConfig().getInt("cooldownSeconds", 60);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    /**
     * Check if a player is on cooldown
     * @param player The player to check
     * @return true if player is on cooldown, false otherwise
     */
    private boolean isOnCooldown(Player player) {
        // Players with bypass permission can skip cooldown
        if (player.hasPermission("minimalrtp.cooldown.bypass")) {
            return false;
        }
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        return cooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    /**
     * Get remaining cooldown time in seconds
     * @param player The player to check
     * @return Remaining cooldown time in seconds
     */
    private long getRemainingCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        long remainingTime = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
        return remainingTime > 0 ? TimeUnit.MILLISECONDS.toSeconds(remainingTime) + 1 : 0;
    }

    private int[] generateRandomLocation(World world, int minDistance, int maxDistance) {
        // Get a random angle
        double angle = Math.random() * 2 * Math.PI;
        // Get a random distance between minDistance and maxDistance
        int distance = minDistance + (int)(Math.random() * (maxDistance - minDistance));
        // Calculate X and Z coordinates
        int x = (int)(Math.cos(angle) * distance);
        int z = (int)(Math.sin(angle) * distance);

        // Find a safe Y coordinate - must be done on main thread
        int[] result = new int[3];
        try {
            // Use CompletableFuture to run this on the main thread and wait for the result
            CompletableFuture<Integer> yFuture = new CompletableFuture<>();

            Bukkit.getScheduler().runTask(MinimalRTP.instance, () -> {
                int y = findSafeY(world, x, z);
                yFuture.complete(y);
            });

            // Wait for the result (this is still in the async thread)
            int y = yFuture.get();

            if (y == -1) {
                return null; // No safe location found
            }

            result[0] = x;
            result[1] = y;
            result[2] = z;
            return result;
        } catch (Exception e) {
            MinimalRTP.instance.getLogger().severe("Error finding safe Y coordinate: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private int findSafeY(World world, int x, int z) {
        // Start from the maximum height and go down
        // Skip air blocks at the top
        int y = world.getMaxHeight() - 1;
        while (y > 0 && world.getBlockAt(x, y, z).getType() == Material.AIR) {
            y--;
        }
        // If we hit the bottom, no safe location
        if (y == 0) {
            return -1;
        }
        // Now we're at a non-air block, check if the blocks above are safe
        if (isSafeLocation(world, x, y + 1, z)) {
            return y + 1;
        }
        return -1; // No safe location found
    }

    private boolean isSafeLocation(World world, int x, int y, int z) {
        // Check if the block at feet and head level is air
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        // Check if standing on a solid block
        boolean solidGround = ground.getType().isSolid();
        // Check if feet and head positions are safe (air)
        boolean safeSpace = feet.getType() == Material.AIR && head.getType() == Material.AIR;
        // Check if not in dangerous blocks
        boolean notDangerous = !ground.isLiquid() &&
                ground.getType() != Material.LAVA &&
                ground.getType() != Material.MAGMA_BLOCK &&
                ground.getType() != Material.CACTUS;
        return solidGround && safeSpace && notDangerous;
    }
}