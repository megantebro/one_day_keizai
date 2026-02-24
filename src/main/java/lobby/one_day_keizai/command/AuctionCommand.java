package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.AuctionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            case "add"    -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
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

    /** 手持ちアイテムをプールに追加（op専用） */
    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("one_day_keizai.auction.add")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ使用可能です。");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "手にアイテムを持ってください。");
            return;
        }

        // 残りの引数を表示名として結合（省略時はnull→自動生成）
        String displayName = args.length >= 2
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : null;

        int index = auctionManager.addItem(held, displayName);
        String name = auctionManager.getItemPoolNames().get(index - 1);
        player.sendMessage(ChatColor.GREEN + "アイテム「" + name + "」をオークションプールに追加しました。(" + index + "番)");
        player.sendMessage(ChatColor.YELLOW + "config.yml に自動保存されました。");
    }

    /** インデックス指定でアイテムをプールから削除（op専用） */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("one_day_keizai.auction.remove")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使い方: /auction remove <番号>");
            sender.sendMessage(ChatColor.YELLOW + "/auction list で番号を確認できます。");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "番号は整数で入力してください。");
            return;
        }

        String removed = auctionManager.removeItem(index);
        if (removed == null) {
            sender.sendMessage(ChatColor.RED + "番号 " + index + " のアイテムが見つかりません。");
            sender.sendMessage(ChatColor.YELLOW + "/auction list で現在のプールを確認してください。");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "「" + removed + "」をオークションプールから削除しました。");
        sender.sendMessage(ChatColor.YELLOW + "config.yml に自動保存されました。");
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
        sender.sendMessage(ChatColor.YELLOW + "  /auction <金額>           - 入札する");
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.list")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction list             - アイテムプール一覧を表示");
        }
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.add")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction add [表示名]     - 手持ちアイテムをプールに追加");
        }
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.remove")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction remove <番号>    - 番号指定でアイテムを削除");
        }
        if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "  /auction reload           - configから再読み込み");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("list"));
            if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.add"))    completions.add("add");
            if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.remove")) completions.add("remove");
            if (sender.isOp() || sender.hasPermission("one_day_keizai.auction.reload")) completions.add("reload");
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return new ArrayList<>();
    }
}
