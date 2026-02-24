package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.AuctionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "list"   -> handleList(sender);
            default -> {
                // 数値なら入札処理
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("プレイヤーのみ使用可能です。");
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[0]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "金額は数値で入力してください。");
                    return true;
                }
                auctionManager.placeBid(player, amount);
            }
        }

        return true;
    }

    /** アイテムプールをconfigからリロード（op専用） */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("one_day_keizai.auction.reload") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        int count = auctionManager.reloadItemPool();
        sender.sendMessage(ChatColor.GREEN + "オークションアイテムプールをリロードしました。(" + count + " 件)");
    }

    /** 現在のアイテムプール一覧を表示 */
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("one_day_keizai.auction.list") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        List<String> names = auctionManager.getItemPoolNames();
        if (names.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "アイテムプールが空です。");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== オークションアイテムプール (" + names.size() + " 件) ===");
        for (int i = 0; i < names.size(); i++) {
            sender.sendMessage(ChatColor.YELLOW + "" + (i + 1) + ". " + ChatColor.WHITE + names.get(i));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /auction <金額>  - 入札する");
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.list")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction list    - アイテムプール一覧を表示");
        }
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction reload  - アイテムプールをconfigから再読み込み");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("list"));
            if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.reload")) {
                completions.add("reload");
            }
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return new ArrayList<>();
    }
}
