package dev.disc.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Builds and identifies disc items and registers their recipes. */
final class DiscItems {
    private final ThrowingDiscPlugin plugin;
    private final NamespacedKey tierKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    DiscItems(ThrowingDiscPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "tier");
    }

    // ---- per-tier config -------------------------------------------------

    double damage(DiscTier tier) {
        return plugin.getConfig().getDouble("tiers." + tier.id() + ".damage", tier.defaultDamage());
    }

    int durability(DiscTier tier) {
        return Math.max(1, plugin.getConfig().getInt("tiers." + tier.id() + ".durability", tier.defaultDurability()));
    }

    Material baseMaterial(DiscTier tier) {
        String name = plugin.getConfig().getString("tiers." + tier.id() + ".base-material", tier.defaultBase().name());
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            return tier.defaultBase();
        }
        return material;
    }

    // ---- item creation / identification -----------------------------------

    ItemStack create(DiscTier tier) {
        ItemStack stack = new ItemStack(baseMaterial(tier));
        ItemMeta meta = stack.getItemMeta();
        meta.itemName(Component.text(tier.displayName()));
        meta.setMaxStackSize(1);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(durability(tier));
            damageable.setDamage(0);
        }
        if (plugin.settings().customModel) {
            meta.setItemModel(new NamespacedKey("throwing_disc", tier.id() + "_throwing_disc"));
        }
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** @return the tier of a disc item, or null if the stack is not a throwing disc. */
    DiscTier tierOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return id == null ? null : DiscTier.byId(id);
    }

    /**
     * Uses one point of durability (respecting Unbreaking).
     *
     * @return the worn stack, or null if the disc broke.
     */
    ItemStack wear(ItemStack stack, DiscTier tier) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return stack;
        }
        int unbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return stack;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : durability(tier);
        int damage = damageable.getDamage() + 1;
        if (damage >= max) {
            return null;
        }
        damageable.setDamage(damage);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Netherite upgrade: same wear proportion as the diamond disc it came from. */
    ItemStack upgraded(ItemStack diamondDisc) {
        ItemStack result = create(DiscTier.NETHERITE);
        ItemMeta oldMeta = diamondDisc.getItemMeta();
        ItemMeta newMeta = result.getItemMeta();
        if (oldMeta instanceof Damageable oldDamage && newMeta instanceof Damageable newDamage) {
            double fraction = oldDamage.getDamage() / (double) durability(DiscTier.DIAMOND);
            int carried = (int) Math.round(fraction * durability(DiscTier.NETHERITE));
            newDamage.setDamage(Math.min(carried, durability(DiscTier.NETHERITE) - 1));
            result.setItemMeta(newMeta);
        }
        return result;
    }

    // ---- recipes ----------------------------------------------------------

    NamespacedKey recipeKey(DiscTier tier) {
        return new NamespacedKey(plugin, tier.id() + "_throwing_disc");
    }

    List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }

    void registerRecipes() {
        for (DiscTier tier : DiscTier.values()) {
            NamespacedKey key = recipeKey(tier);
            if (tier.ingredient() != null) {
                ShapedRecipe recipe = new ShapedRecipe(key, create(tier));
                recipe.shape(" M ", "M M", " M ");
                recipe.setIngredient('M', tier.ingredient());
                recipe.setCategory(CraftingBookCategory.EQUIPMENT);
                Bukkit.addRecipe(recipe);
            } else {
                // Netherite: smithing upgrade of a diamond disc. The base is matched by material here;
                // DiscListener rejects it unless it is really a diamond disc and carries the wear over.
                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                        key,
                        create(tier),
                        new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        new RecipeChoice.MaterialChoice(baseMaterial(DiscTier.DIAMOND)),
                        new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
                Bukkit.addRecipe(recipe);
            }
            recipeKeys.add(key);
        }
    }

    void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
    }
}
