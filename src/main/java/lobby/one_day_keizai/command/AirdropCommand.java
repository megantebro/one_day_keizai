package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.AirdropManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /airdrop コマンド（OP専用）
 *
 * サブコマンド:
 *   /airdrop spawn           - ランダム座標にエアドロップを手動生成
 *   /airdrop key [player]    - エアドロップの鍵を配布
 */
public class AirdropCommand implements CommandExecutor {

    private final AirdropManager airdropManager;

    public AirdropCommand(AirdropManager airdropManager) {
        this.airdropManager = airdropManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("one_day_keizai.admin")) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOPのみ使用できます。");
            return true;
        }

        if (args.length == 0) {
            printHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                airdropManager.spawnRandom();
                sender.sendMessage(ChatColor.GREEN + "エアドロップを手動生成しました。");
            }
            case "key" -> {
                Player target = resolveTarget(sender, args);
                if (target == null) return true;
                target.getInventory().addItem(airdropManager.createKey());
                sender.sendMessage(ChatColor.GREEN + target.getName() + " にエアドロップの鍵を渡しました。");
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.GOLD + "エアドロップの鍵を受け取りました！");
                }
            }
            default -> printHelp(sender);
        }
        return true;
    }

    private void printHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== /airdrop ===");
        sender.sendMessage(ChatColor.YELLOW + "/airdrop spawn        " + ChatColor.GRAY + "- エアドロップを手動生成");
        sender.sendMessage(ChatColor.YELLOW + "/airdrop key [player] " + ChatColor.GRAY + "- 鍵を配布");
    }

    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player t = Bukkit.getPlayer(args[1]);
            if (t == null) {
                sender.sendMessage(ChatColor.RED + "プレイヤー '" + args[1] + "' が見つかりません。");
            }
            return t;
        } else if (sender instanceof Player p) {
            return p;
        } else {
            sender.sendMessage(ChatColor.RED + "プレイヤー名を指定してください: /airdrop key <player>");
            return null;
        }
    }
}
