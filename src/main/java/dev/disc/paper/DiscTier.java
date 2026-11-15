package dev.disc.paper;

import org.bukkit.Material;

/** The five disc tiers. Numbers here are defaults; config.yml can override them. */
public enum DiscTier {
    COPPER("copper", "Copper Throwing Disc", Material.MUSIC_DISC_CHIRP, Material.COPPER_INGOT, 4.0, 95),
    IRON("iron", "Iron Throwing Disc", Material.MUSIC_DISC_STAL, Material.IRON_INGOT, 5.0, 125),
    GOLD("gold", "Golden Throwing Disc", Material.MUSIC_DISC_WARD, Material.GOLD_INGOT, 4.0, 16),
    DIAMOND("diamond", "Diamond Throwing Disc", Material.MUSIC_DISC_FAR, Material.DIAMOND, 7.0, 780),
    // Netherite is a smithing upgrade of the diamond disc, so it has no crafting ingredient.
    NETHERITE("netherite", "Netherite Throwing Disc", Material.MUSIC_DISC_PIGSTEP, null, 8.0, 1015);

    private final String id;
    private final String displayName;
    private final Material defaultBase;
    private final Material ingredient;
    private final double defaultDamage;
    private final int defaultDurability;

    DiscTier(String id, String displayName, Material defaultBase, Material ingredient,
             double defaultDamage, int defaultDurability) {
        this.id = id;
        this.displayName = displayName;
        this.defaultBase = defaultBase;
        this.ingredient = ingredient;
        this.defaultDamage = defaultDamage;
        this.defaultDurability = defaultDurability;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material defaultBase() { return defaultBase; }
    public Material ingredient() { return ingredient; }
    public double defaultDamage() { return defaultDamage; }
    public int defaultDurability() { return defaultDurability; }

    public static DiscTier byId(String id) {
        for (DiscTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }
}
