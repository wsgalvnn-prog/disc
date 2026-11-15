package dev.disc.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns every disc currently in the air and ticks them once per server tick. */
final class FlightManager {
    private record Hit(String attacker, int tick) { }

    private final ThrowingDiscPlugin plugin;
    private final List<DiscFlight> flights = new ArrayList<>();
    private final Map<UUID, Hit> recentHits = new HashMap<>();
    private final Map<UUID, Integer> lastThrow = new HashMap<>();
    private BukkitTask task;

    FlightManager(ThrowingDiscPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    private void tickAll() {
        for (DiscFlight flight : new ArrayList<>(flights)) {
            try {
                flight.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Disc flight failed, dropping the disc: " + ex);
                flight.dropStack();
            }
        }
        flights.removeIf(DiscFlight::isDone);
        recentHits.values().removeIf(hit -> plugin.getServer().getCurrentTick() - hit.tick() > 5);
    }

    /** Throw the disc a player is holding. */
    void throwDisc(Player player, EquipmentSlot hand, ItemStack held, DiscTier tier) {
        int now = player.getTicksLived();
        Integer last = lastThrow.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().cooldownTicks) {
            return;
        }
        lastThrow.put(player.getUniqueId(), now);

        int slot = hand == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot();
        ItemStack single = held.clone();
        single.setAmount(1);
        ItemStack worn = plugin.items().wear(single, tier);

        ItemStack empty = new ItemStack(Material.AIR);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(empty);
        } else {
            player.getInventory().setItemInMainHand(empty);
        }

        if (worn == null) {
            // That throw used up the last durability: the disc breaks instead of flying.
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.8f, 1.0f);
            return;
        }

        flights.add(new DiscFlight(plugin, player, worn, slot, tier));
        player.sendActionBar(Component.text("Disc in flight"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.65f, 1.45f);
    }

    /** Called when a player logs out: any disc of theirs still flying is caught (or dropped). */
    void recoverAll(Player player) {
        for (DiscFlight flight : new ArrayList<>(flights)) {
            if (!flight.isDone() && flight.throwerId().equals(player.getUniqueId())) {
                flight.recoverFor(player);
            }
        }
        flights.removeIf(DiscFlight::isDone);
        lastThrow.remove(player.getUniqueId());
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (DiscFlight flight : new ArrayList<>(flights)) {
            Player thrower = Bukkit.getPlayer(flight.throwerId());
            if (thrower != null) {
                flight.recoverFor(thrower);
            } else {
                flight.dropStack();
            }
        }
        flights.clear();
        recentHits.clear();
    }

    // ---- death messages ----------------------------------------------------------------

    void recordHit(UUID victim, String attackerName) {
        recentHits.put(victim, new Hit(attackerName, plugin.getServer().getCurrentTick()));
    }

    /** @return the name of whoever just hit this player with a disc, or null. */
    String recentAttacker(UUID victim) {
        Hit hit = recentHits.get(victim);
        if (hit == null || plugin.getServer().getCurrentTick() - hit.tick() > 2) {
            return null;
        }
        return hit.attacker();
    }
}
