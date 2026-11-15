package dev.disc.paper;

import org.bukkit.configuration.file.FileConfiguration;

/** Snapshot of config.yml values, reloaded by /throwingdisc reload. */
final class Settings {
    final boolean armorPiercing;
    final boolean customModel;
    final boolean discoverRecipes;
    final String packUrl;
    final String packSha1;
    final boolean packForce;
    final int shieldDisableTicks;

    final double speed;
    final double maxSpeed;
    final double bounceBoost;
    final int maxBounces;
    final int maxAge;
    final int hardMaxAge;
    final double baseDistance;
    final double distancePerBounce;
    final int cooldownTicks;

    private Settings(FileConfiguration c) {
        armorPiercing = c.getBoolean("armor-piercing", true);
        customModel = c.getBoolean("custom-model", false);
        discoverRecipes = c.getBoolean("discover-recipes", true);
        packUrl = c.getString("resource-pack.url", "");
        packSha1 = c.getString("resource-pack.sha1", "");
        packForce = c.getBoolean("resource-pack.force", false);
        shieldDisableTicks = c.getInt("shield-disable-ticks", 100);

        speed = c.getDouble("flight.speed", 1.25);
        maxSpeed = c.getDouble("flight.max-speed", 3.0);
        bounceBoost = c.getDouble("flight.bounce-speed-boost", 1.15);
        maxBounces = c.getInt("flight.max-bounces", 8);
        maxAge = c.getInt("flight.max-age-ticks", 160);
        hardMaxAge = c.getInt("flight.hard-max-age-ticks", 320);
        baseDistance = c.getDouble("flight.base-distance", 18.0);
        distancePerBounce = c.getDouble("flight.distance-per-bounce", 12.0);
        cooldownTicks = c.getInt("flight.cooldown-ticks", 4);
    }

    static Settings load(FileConfiguration config) {
        return new Settings(config);
    }
}
