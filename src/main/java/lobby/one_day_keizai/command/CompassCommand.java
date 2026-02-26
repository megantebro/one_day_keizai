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
 * /compass shop      — ショップコンパスを入手
 * /compass wanted    — 賞金首コンパスを入手
 * /compass setshop   — 現在地をショップコンパスの目標地点に設定（OP専用）
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
            player.sendMessage(ChatColor.YELLOW + "/compass shop     — ショップコンパスを入手");
            player.sendMessage(ChatColor.YELLOW + "/compass wanted   — 賞金首コンパスを入手");
            if (player.isOp()) {
                player.sendMessage(ChatColor.YELLOW + "/compass setshop  — 現在地をショップ目標地点に設定（OP）");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "shop" -> {
                player.getInventory().addItem(compassManager.createShopCompass());
                player.sendMessage(ChatColor.AQUA + "ショップコンパスを入手しました！");
                player.sendMessage(ChatColor.GRAY + "手に持つとショップ方向を指し示します。");
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
                Location loc = player.getLocation();
                compassManager.setShopTarget(loc);
                player.sendMessage(ChatColor.GREEN + "ショップコンパスの目標地点を設定しました：");
                player.sendMessage(ChatColor.GRAY + String.format("  ワールド: %s  X: %.1f  Y: %.1f  Z: %.1f",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
            }
            default -> {
                player.sendMessage(ChatColor.RED + "使い方: /compass <shop|wanted" + (player.isOp() ? "|setshop" : "") + ">");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            boolean isOp = (sender instanceof Player p) && p.isOp();
            List<String> options = isOp
                    ? Arrays.asList("shop", "wanted", "setshop")
                    : Arrays.asList("shop", "wanted");
            return options.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
