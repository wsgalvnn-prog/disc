package dev.disc.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class DiscListener implements Listener {
    private final ThrowingDiscPlugin plugin;

    DiscListener(ThrowingDiscPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-click with a disc throws it. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        ItemStack held = event.getItem();
        DiscTier tier = plugin.items().tierOf(held);
        if (hand == null || tier == null) {
            return;
        }
        Player player = event.getPlayer();

        // Sneaking with discs in both hands: the main hand passes, so the off-hand disc is thrown.
        if (hand == EquipmentSlot.HAND && player.isSneaking()
                && plugin.items().tierOf(player.getInventory().getItemInOffHand()) != null) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked != null && !player.isSneaking()) {
            // The discs are music-disc based: never let them go into a jukebox.
            if (clicked.getType() == Material.JUKEBOX) {
                event.setCancelled(true);
                return;
            }
            // Let chests, doors, buttons etc. work normally instead of throwing.
            if (clicked.getType().isInteractable()) {
                return;
            }
        }

        event.setCancelled(true);
        plugin.flights().throwDisc(player, hand, held, tier);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.flights().recoverAll(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (settings.discoverRecipes) {
            player.discoverRecipes(plugin.items().recipeKeys());
        }
        if (!settings.packUrl.isBlank()) {
            player.setResourcePack(settings.packUrl, hexToBytes(settings.packSha1), settings.packForce);
        }
    }

    /** Netherite upgrade only accepts a real diamond disc, and keeps its wear. */
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || plugin.items().tierOf(result) != DiscTier.NETHERITE) {
            return;
        }
        ItemStack base = event.getInventory().getInputEquipment();
        if (plugin.items().tierOf(base) != DiscTier.DIAMOND) {
            event.setResult(null);
            return;
        }
        event.setResult(plugin.items().upgraded(base));
    }

    /** Armor-piercing hits use a magic-style damage source; give it a proper death message. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String attacker = plugin.flights().recentAttacker(event.getPlayer().getUniqueId());
        if (attacker != null) {
            event.deathMessage(Component.text(event.getPlayer().getName() + " was killed by " + attacker
                    + " with a Throwing Disc"));
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() != 40) {
            return null;
        }
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
