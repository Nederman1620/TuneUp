package dev.louis.tuneup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

final class TuneUpCommand implements TabExecutor {

    private final TuneUpPlugin plugin;

    TuneUpCommand(TuneUpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                send(sender, "Config reloaded.", NamedTextColor.GREEN);
            }
            case "status" -> status(sender);
            case "books" -> books(sender, args);
            default -> send(sender, "Usage: /" + label + " <status|reload|books>", NamedTextColor.RED);
        }
        return true;
    }

    private void books(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Book boost is " + (plugin.booksEnabled() ? "ON" : "OFF")
                    + ". Use /tuneup books <on|off|toggle>", NamedTextColor.YELLOW);
            return;
        }
        Boolean enable = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            case "toggle" -> Boolean.valueOf(!plugin.booksEnabled());
            default -> null;
        };
        if (enable == null) {
            send(sender, "Usage: /tuneup books <on|off|toggle>", NamedTextColor.RED);
            return;
        }
        plugin.setBooksEnabled(enable);
        send(sender, enable ? "Book boost enabled." : "Book boost disabled.",
                enable ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private void status(CommandSender sender) {
        double[] tps = Bukkit.getTPS();
        send(sender, String.format(Locale.ROOT, "TPS (1m/5m/15m): %.2f / %.2f / %.2f   MSPT: %.1f",
                Math.min(20.0, tps[0]), Math.min(20.0, tps[1]), Math.min(20.0, tps[2]),
                Bukkit.getAverageTickTime()), NamedTextColor.GOLD);

        for (World world : Bukkit.getWorlds()) {
            send(sender, world.getName() + ": simulation " + world.getSimulationDistance()
                    + ", view " + world.getViewDistance(), NamedTextColor.GRAY);
        }

        EntityLimiter limiter = plugin.limiter();
        send(sender, limiter == null
                ? "Entity limiter: off"
                : "Entity limiter: removed " + limiter.removedMobs() + " mobs, "
                + limiter.removedItems() + " items", NamedTextColor.YELLOW);

        DynamicDistance distance = plugin.distance();
        send(sender, distance == null
                ? "Dynamic distance: off"
                : "Dynamic distance: lowered " + distance.reductions() + " times", NamedTextColor.YELLOW);

        BookBoost books = plugin.books();
        send(sender, books == null ? "Book boost: off" : "Book boost: " + books.stats(), NamedTextColor.YELLOW);
    }

    private void send(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "books").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("books")) {
            return List.of("on", "off", "toggle").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
