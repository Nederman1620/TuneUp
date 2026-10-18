package dev.louis.tuneup;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Makes the most useful enchanted books easier to find through normal gameplay. */
final class BookBoost implements Listener {

    private record Entry(Enchantment enchantment, int weight) {
    }

    private final TuneUpPlugin plugin;
    private final List<Entry> pool = new ArrayList<>();
    private int totalWeight;

    private final boolean villagersOn;
    private final double villagerChance;
    private final boolean villagerMaxLevel;
    private final int villagerMaxCost;

    private final boolean fishingOn;
    private final double fishReplaceChance;
    private final double fishBonusChance;

    private final boolean lootOn;
    private final double lootChance;

    private final boolean tableOn;
    private final double tableChance;
    private final int tableMinCost;

    private int villagerSwaps;
    private int fishingSwaps;
    private int lootAdds;
    private int tableSwaps;

    BookBoost(TuneUpPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();

        ConfigurationSection preferred = cfg.getConfigurationSection("books.preferred");
        if (preferred != null) {
            for (String key : preferred.getKeys(false)) {
                int weight = preferred.getInt(key, 0);
                if (weight <= 0) {
                    continue;
                }
                Enchantment enchantment = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    plugin.getLogger().warning("Unknown enchantment in books.preferred: " + key);
                    continue;
                }
                pool.add(new Entry(enchantment, weight));
                totalWeight += weight;
            }
        }
        if (pool.isEmpty()) {
            plugin.getLogger().warning("books.preferred has no valid enchantments; book boost does nothing.");
        }

        villagersOn = cfg.getBoolean("books.villagers.enabled", true);
        villagerChance = cfg.getDouble("books.villagers.chance", 0.65);
        villagerMaxLevel = cfg.getBoolean("books.villagers.always-max-level", true);
        villagerMaxCost = Math.max(1, Math.min(64, cfg.getInt("books.villagers.max-cost", 40)));

        fishingOn = cfg.getBoolean("books.fishing.enabled", true);
        fishReplaceChance = cfg.getDouble("books.fishing.replace-chance", 0.85);
        fishBonusChance = cfg.getDouble("books.fishing.bonus-chance", 0.01);

        lootOn = cfg.getBoolean("books.loot-chests.enabled", true);
        lootChance = cfg.getDouble("books.loot-chests.chance", 0.10);

        tableOn = cfg.getBoolean("books.enchanting-table.enabled", true);
        tableChance = cfg.getDouble("books.enchanting-table.chance", 0.5);
        tableMinCost = cfg.getInt("books.enchanting-table.min-level-cost", 20);
    }

    String stats() {
        return "swaps: " + villagerSwaps + " trades, " + fishingSwaps + " fishing, "
                + lootAdds + " chest books, " + tableSwaps + " table books";
    }

    // ---------------------------------------------------------------- helpers

    private boolean roll(double chance) {
        return !pool.isEmpty() && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private Enchantment pick() {
        if (pool.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Entry entry : pool) {
            r -= entry.weight();
            if (r < 0) {
                return entry.enchantment();
            }
        }
        return pool.get(pool.size() - 1).enchantment();
    }

    private ItemStack book(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);
        book.setItemMeta(meta);
        return book;
    }

    /** Takes the better of two rolls, so higher levels are favoured. */
    private int luckyLevel(Enchantment enchantment) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int max = enchantment.getMaxLevel();
        return Math.max(rng.nextInt(max) + 1, rng.nextInt(max) + 1);
    }

    // ----------------------------------------------------------------- events

    @EventHandler(ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!villagersOn
                || !(event.getEntity() instanceof Villager villager)
                || villager.getProfession() != Villager.Profession.LIBRARIAN) {
            return;
        }
        MerchantRecipe old = event.getRecipe();
        if (old.getResult().getType() != Material.ENCHANTED_BOOK || !roll(villagerChance)) {
            return;
        }
        Enchantment enchantment = pick();
        if (enchantment == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int level = villagerMaxLevel ? enchantment.getMaxLevel() : rng.nextInt(enchantment.getMaxLevel()) + 1;
        int cost = 2 + rng.nextInt(5 + level * 10) + 3 * level;
        cost = Math.max(1, Math.min(villagerMaxCost, cost));

        MerchantRecipe recipe = new MerchantRecipe(
                book(enchantment, level),
                0,
                old.getMaxUses(),
                old.hasExperienceReward(),
                old.getVillagerExperience(),
                old.getPriceMultiplier());
        recipe.setIngredients(List.of(new ItemStack(Material.EMERALD, cost), new ItemStack(Material.BOOK)));
        event.setRecipe(recipe);
        villagerSwaps++;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!fishingOn
                || event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Material type = caught.getItemStack().getType();
        boolean swap;
        if (type == Material.ENCHANTED_BOOK) {
            swap = roll(fishReplaceChance);
        } else if (Tag.ITEMS_FISHES.isTagged(type)) {
            swap = roll(fishBonusChance);
        } else {
            swap = false;
        }
        if (!swap) {
            return;
        }
        Enchantment enchantment = pick();
        if (enchantment == null) {
            return;
        }
        caught.setItemStack(book(enchantment, luckyLevel(enchantment)));
        fishingSwaps++;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!lootOn || !event.getLootTable().getKey().getKey().startsWith("chests/") || !roll(lootChance)) {
            return;
        }
        Enchantment enchantment = pick();
        if (enchantment == null) {
            return;
        }
        event.getLoot().add(book(enchantment, luckyLevel(enchantment)));
        lootAdds++;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!tableOn
                || event.getItem().getType() != Material.BOOK
                || event.getExpLevelCost() < tableMinCost
                || !roll(tableChance)) {
            return;
        }
        Enchantment enchantment = pick();
        if (enchantment == null) {
            return;
        }
        int max = enchantment.getMaxLevel();
        int level = Math.max(1, Math.min(max, (int) Math.ceil(max * event.getExpLevelCost() / 30.0)));
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        toAdd.clear();
        toAdd.put(enchantment, level);
        tableSwaps++;
    }
}
