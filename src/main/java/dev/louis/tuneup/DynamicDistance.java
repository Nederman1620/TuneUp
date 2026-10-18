package dev.louis.tuneup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lowers simulation distance when TPS drops and restores it when the server
 * recovers. Original values are remembered and put back on disable/reload.
 */
final class DynamicDistance {

    private final TuneUpPlugin plugin;
    private final Map<UUID, Integer> originalSimulation = new HashMap<>();
    private final Map<UUID, Integer> originalView = new HashMap<>();
    private final Set<String> disabledWorlds = new HashSet<>();

    private final long checkTicks;
    private final double lowTps;
    private final double highTps;
    private final int streakRequired;
    private final int minSimulation;
    private final boolean adjustView;
    private final int minView;

    private int lowStreak;
    private int highStreak;
    private int reductions;

    DynamicDistance(TuneUpPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        String p = "dynamic-distance.";
        this.checkTicks = Math.max(1, cfg.getInt(p + "check-interval-seconds", 10)) * 20L;
        this.lowTps = cfg.getDouble(p + "low-tps", 17.5);
        this.highTps = cfg.getDouble(p + "high-tps", 19.5);
        this.streakRequired = Math.max(1, cfg.getInt(p + "streak-required", 3));
        this.minSimulation = Math.max(2, cfg.getInt(p + "min-simulation-distance", 4));
        this.adjustView = cfg.getBoolean(p + "adjust-view-distance", false);
        this.minView = Math.max(2, cfg.getInt(p + "min-view-distance", 6));
        disabledWorlds.addAll(cfg.getStringList("disabled-worlds"));
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 200L, checkTicks);
    }

    int reductions() {
        return reductions;
    }

    private void check() {
        double tps = Bukkit.getTPS()[0];

        if (tps < lowTps) {
            lowStreak++;
            highStreak = 0;
        } else if (tps > highTps) {
            highStreak++;
            lowStreak = 0;
        } else {
            lowStreak = 0;
            highStreak = 0;
        }

        if (lowStreak >= streakRequired) {
            lowStreak = 0;
            adjust(-1);
        } else if (highStreak >= streakRequired) {
            highStreak = 0;
            adjust(+1);
        }
    }

    private void adjust(int direction) {
        for (World world : Bukkit.getWorlds()) {
            if (disabledWorlds.contains(world.getName())) {
                continue;
            }
            UUID id = world.getUID();
            int origSim = originalSimulation.computeIfAbsent(id, k -> world.getSimulationDistance());
            int currentSim = world.getSimulationDistance();
            int newSim = direction < 0
                    ? Math.max(minSimulation, currentSim - 1)
                    : Math.min(origSim, currentSim + 1);
            if (newSim != currentSim) {
                world.setSimulationDistance(newSim);
                if (direction < 0) {
                    reductions++;
                }
            }

            if (adjustView) {
                int origView = originalView.computeIfAbsent(id, k -> world.getViewDistance());
                int currentView = world.getViewDistance();
                int newView = direction < 0
                        ? Math.max(minView, currentView - 1)
                        : Math.min(origView, currentView + 1);
                if (newView != currentView) {
                    world.setViewDistance(newView);
                }
            }
        }
    }

    /** Put every world back to the distances it had before TuneUp touched it. */
    void restore() {
        for (Map.Entry<UUID, Integer> entry : originalSimulation.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world != null) {
                world.setSimulationDistance(entry.getValue());
            }
        }
        for (Map.Entry<UUID, Integer> entry : originalView.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world != null) {
                world.setViewDistance(entry.getValue());
            }
        }
        originalSimulation.clear();
        originalView.clear();
    }
}
