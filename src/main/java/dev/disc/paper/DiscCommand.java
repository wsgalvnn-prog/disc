package dev.disc.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /throwingdisc give <player> <tier> [amount]  and  /throwingdisc reload */
final class DiscCommand implements TabExecutor {
    private final ThrowingDiscPlugin plugin;

    DiscCommand(ThrowingDiscPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("throwingdisc.reload")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            plugin.reload();
            sender.sendMessage(Component.text("Throwing Discs config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("throwingdisc.give")) {
                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            DiscTier tier = DiscTier.byId(args[2]);
            if (tier == null) {
                sender.sendMessage(Component.text("Unknown tier: " + args[2]
                        + " (copper, iron, gold, diamond, netherite)", NamedTextColor.RED));
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                    return true;
                }
            }
            for (int i = 0; i < amount; i++) {
                for (ItemStack leftover : target.getInventory().addItem(plugin.items().create(tier)).values()) {
                    target.getWorld().dropItem(target.getLocation(), leftover);
                }
            }
            sender.sendMessage(Component.text("Gave " + amount + " x " + tier.displayName() + " to "
                    + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /" + label + " give <player> <tier> [amount] | reload",
                NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("throwingdisc.give")) {
                out.add("give");
            }
            if (sender.hasPermission("throwingdisc.reload")) {
                out.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (DiscTier tier : DiscTier.values()) {
                out.add(tier.id());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            out.add("1");
            out.add("5");
            out.add("16");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
