package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.CompassManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /compass shop              — ショップコンパスを入手
 * /compass wanted            — 賞金首コンパスを入手
 * /compass setshop front     — 現在地をコンパス反応エリア（入口）に設定（OP専用）
 * /compass setshop back      — 現在地をTP先（ショップ内）に設定（OP専用）
 */
public class CompassCommand implements CommandExecutor, TabCompleter {

    private final CompassManager compassManager;

    public CompassCommand(CompassManager compassManager) {
        this.compassManager = compassManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ使用可能です。");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "shop" -> {
                player.getInventory().addItem(compassManager.createShopCompass());
                player.sendMessage(ChatColor.AQUA + "ショップコンパスを入手しました！");
                player.sendMessage(ChatColor.GRAY + "入口付近で右クリックするとショップへTP します。");
            }
            case "wanted" -> {
                player.getInventory().addItem(compassManager.createWantedCompass());
                player.sendMessage(ChatColor.RED + "賞金首コンパスを入手しました！");
                player.sendMessage(ChatColor.GRAY + "手に持つと最寄りの指名手配プレイヤーを追跡します。");
            }
            case "setshop" -> {
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "このコマンドはOPのみ使用可能です。");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使い方: /compass setshop <front|back>");
                    player.sendMessage(ChatColor.GRAY + "  front … コンパスが反応する入口地点");
                    player.sendMessage(ChatColor.GRAY + "  back  … TP先（ショップ内）");
                    return true;
                }
                Location loc = player.getLocation();
                switch (args[1].toLowerCase()) {
                    case "front" -> {
                        compassManager.setShopFront(loc);
                        player.sendMessage(ChatColor.GREEN + "ショップ入口（front）を設定しました：");
                        player.sendMessage(formatLoc(loc));
                    }
                    case "back" -> {
                        compassManager.setShopBack(loc);
                        player.sendMessage(ChatColor.GREEN + "ショップTP先（back）を設定しました：");
                        player.sendMessage(formatLoc(loc));
                    }
                    default -> player.sendMessage(ChatColor.RED + "使い方: /compass setshop <front|back>");
                }
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "/compass shop     — ショップコンパスを入手");
        player.sendMessage(ChatColor.YELLOW + "/compass wanted   — 賞金首コンパスを入手");
        if (player.isOp()) {
            player.sendMessage(ChatColor.YELLOW + "/compass setshop front — 入口地点を設定（OP）");
            player.sendMessage(ChatColor.YELLOW + "/compass setshop back  — TP先を設定（OP）");
        }
    }

    private String formatLoc(Location loc) {
        return ChatColor.GRAY + String.format("  ワールド: %s  X: %.1f  Y: %.1f  Z: %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isOp = (sender instanceof Player p) && p.isOp();

        if (args.length == 1) {
            List<String> options = isOp
                    ? Arrays.asList("shop", "wanted", "setshop")
                    : Arrays.asList("shop", "wanted");
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setshop") && isOp) {
            return Arrays.asList("front", "back").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
