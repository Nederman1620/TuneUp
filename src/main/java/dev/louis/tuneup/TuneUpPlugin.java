package dev.louis.tuneup;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class TuneUpPlugin extends JavaPlugin {

    private EntityLimiter limiter;
    private DynamicDistance distance;
    private BookBoost books;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginCommand command = getCommand("tuneup");
        if (command != null) {
            TuneUpCommand handler = new TuneUpCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        startModules();
    }

    @Override
    public void onDisable() {
        stopModules();
    }

    void reload() {
        stopModules();
        reloadConfig();
        startModules();
    }

    private void startModules() {
        if (getConfig().getBoolean("entity-limiter.enabled", true)) {
            limiter = new EntityLimiter(this);
            limiter.start();
        }
        if (getConfig().getBoolean("dynamic-distance.enabled", true)) {
            distance = new DynamicDistance(this);
            distance.start();
        }
        if (getConfig().getBoolean("books.enabled", true)) {
            books = new BookBoost(this);
            getServer().getPluginManager().registerEvents(books, this);
        }
    }

    private void stopModules() {
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll(this);
        if (distance != null) {
            distance.restore();
        }
        limiter = null;
        distance = null;
        books = null;
    }

    boolean booksEnabled() {
        return books != null;
    }

    /** Switches the book boost on or off at runtime and saves the choice to config.yml. */
    void setBooksEnabled(boolean enabled) {
        getConfig().set("books.enabled", enabled);
        saveConfig();
        if (enabled && books == null) {
            books = new BookBoost(this);
            getServer().getPluginManager().registerEvents(books, this);
        } else if (!enabled && books != null) {
            HandlerList.unregisterAll(books);
            books = null;
        }
    }

    EntityLimiter limiter() {
        return limiter;
    }

    DynamicDistance distance() {
        return distance;
    }

    BookBoost books() {
        return books;
    }
}
