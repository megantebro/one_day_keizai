package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.CompassManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /compass shop   — ショップコンパスを入手
 * /compass wanted — 賞金首コンパスを入手
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
            player.sendMessage(ChatColor.YELLOW + "/compass shop   — ショップコンパスを入手");
            player.sendMessage(ChatColor.YELLOW + "/compass wanted — 賞金首コンパスを入手");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "shop" -> {
                player.getInventory().addItem(compassManager.createShopCompass());
                player.sendMessage(ChatColor.AQUA + "ショップコンパスを入手しました！");
                player.sendMessage(ChatColor.GRAY + "手に持つと最寄りのショップを指し示します。");
            }
            case "wanted" -> {
                player.getInventory().addItem(compassManager.createWantedCompass());
                player.sendMessage(ChatColor.RED + "賞金首コンパスを入手しました！");
                player.sendMessage(ChatColor.GRAY + "手に持つと最寄りの指名手配プレイヤーを追跡します。");
            }
            default -> {
                player.sendMessage(ChatColor.RED + "使い方: /compass <shop|wanted>");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("shop", "wanted").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
