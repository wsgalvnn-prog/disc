package dev.disc.paper;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ThrowingDiscPlugin extends JavaPlugin {
    private Settings settings;
    private DiscItems items;
    private FlightManager flights;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        items = new DiscItems(this);
        items.registerRecipes();
        flights = new FlightManager(this);
        flights.start();

        getServer().getPluginManager().registerEvents(new DiscListener(this), this);

        PluginCommand command = getCommand("throwingdisc");
        if (command != null) {
            DiscCommand handler = new DiscCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (flights != null) {
            flights.shutdown();
        }
        if (items != null) {
            items.unregisterRecipes();
        }
    }

    /** Re-read config.yml. Discs already in flight keep flying; recipes are rebuilt. */
    public void reload() {
        reloadConfig();
        settings = Settings.load(getConfig());
        items.unregisterRecipes();
        items.registerRecipes();
    }

    Settings settings() { return settings; }
    DiscItems items() { return items; }
    FlightManager flights() { return flights; }
}
