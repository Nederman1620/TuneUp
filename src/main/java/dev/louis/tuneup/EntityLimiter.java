package dev.louis.tuneup;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Trims overcrowded chunks. Chunks are queued and processed a few per tick so
 * the limiter itself never causes a lag spike.
 */
final class EntityLimiter {

    private final TuneUpPlugin plugin;
    private final Deque<Chunk> queue = new ArrayDeque<>();

    private final int intervalTicks;
    private final int chunksPerTick;
    private final int minEntities;
    private final int hostileCap;
    private final int otherCap;
    private final int itemCap;
    private final Set<String> exemptTypes = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();
    private final List<String> protectedItems = new ArrayList<>();

    private int idleTicks;
    private long removedMobs;
    private long removedItems;

    EntityLimiter(TuneUpPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();
        String p = "entity-limiter.";
        this.intervalTicks = Math.max(1, cfg.getInt(p + "scan-interval-seconds", 30)) * 20;
        this.chunksPerTick = Math.max(1, cfg.getInt(p + "chunks-per-tick", 20));
        this.minEntities = Math.max(1, cfg.getInt(p + "min-entities-to-scan", 24));
        this.hostileCap = Math.max(1, cfg.getInt(p + "hostile-cap-per-type", 24));
        this.otherCap = Math.max(1, cfg.getInt(p + "other-cap-per-type", 48));
        this.itemCap = Math.max(1, cfg.getInt(p + "item-cap-per-chunk", 150));
        for (String s : cfg.getStringList(p + "exempt-types")) {
            exemptTypes.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : cfg.getStringList(p + "protected-items")) {
            protectedItems.add(s.toUpperCase(Locale.ROOT));
        }
        disabledWorlds.addAll(cfg.getStringList("disabled-worlds"));
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    long removedMobs() {
        return removedMobs;
    }

    long removedItems() {
        return removedItems;
    }

    private void tick() {
        if (queue.isEmpty()) {
            if (++idleTicks < intervalTicks) {
                return;
            }
            idleTicks = 0;
            for (World world : plugin.getServer().getWorlds()) {
                if (!disabledWorlds.contains(world.getName())) {
                    queue.addAll(Arrays.asList(world.getLoadedChunks()));
                }
            }
            return;
        }
        for (int i = 0; i < chunksPerTick && !queue.isEmpty(); i++) {
            handle(queue.poll());
        }
    }

    private void handle(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded() || !chunk.isEntitiesLoaded()) {
            return;
        }
        Entity[] all = chunk.getEntities();
        if (all.length < minEntities) {
            return;
        }

        Map<String, List<Mob>> mobsByType = new HashMap<>();
        List<Item> items = new ArrayList<>();

        for (Entity entity : all) {
            if (entity instanceof Item item) {
                items.add(item);
            } else if (entity instanceof Mob mob && isCullable(mob)) {
                mobsByType.computeIfAbsent(mob.getType().getKey().getKey(), k -> new ArrayList<>()).add(mob);
            }
        }

        for (List<Mob> group : mobsByType.values()) {
            int cap = group.get(0) instanceof Enemy ? hostileCap : otherCap;
            if (group.size() <= cap) {
                continue;
            }
            group.sort(Comparator.comparingInt(Mob::getTicksLived).reversed());
            int excess = group.size() - cap;
            for (int i = 0; i < excess; i++) {
                group.get(i).remove();
                removedMobs++;
            }
        }

        if (items.size() > itemCap) {
            List<Item> removable = new ArrayList<>();
            for (Item item : items) {
                if (!isProtected(item.getItemStack())) {
                    removable.add(item);
                }
            }
            removable.sort(Comparator.comparingInt(Item::getTicksLived).reversed());
            int excess = items.size() - itemCap;
            for (int i = 0; i < excess && i < removable.size(); i++) {
                removable.get(i).remove();
                removedItems++;
            }
        }
    }

    private boolean isCullable(Mob mob) {
        if (exemptTypes.contains(mob.getType().getKey().getKey())) {
            return false;
        }
        if (mob.customName() != null || mob.isLeashed() || mob.isInsideVehicle()) {
            return false;
        }
        if (!mob.getPassengers().isEmpty()) {
            return false;
        }
        return !(mob instanceof Tameable tameable && tameable.isTamed());
    }

    private boolean isProtected(ItemStack stack) {
        String name = stack.getType().name();
        for (String pattern : protectedItems) {
            if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
            if (pattern.startsWith("*") && name.endsWith(pattern.substring(1))) {
                return true;
            }
            if (pattern.equals(name)) {
                return true;
            }
        }
        if (!stack.getEnchantments().isEmpty()) {
            return true;
        }
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            return meta != null && meta.hasDisplayName();
        }
        return false;
    }
}
