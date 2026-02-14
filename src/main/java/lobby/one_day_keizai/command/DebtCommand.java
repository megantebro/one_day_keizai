package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.DebtManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DebtCommand implements CommandExecutor, TabCompleter {

    private final DebtManager debtManager;
    private final Economy economy;

    public DebtCommand(DebtManager debtManager, Economy economy) {
        this.debtManager = debtManager;
        this.economy = economy;
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
            case "lend" -> handleLend(player, args);
            case "repay" -> handleRepay(player, args);
            case "forgive" -> handleForgive(player, args);
            case "list" -> handleList(player);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleLend(Player lender, String[] args) {
        if (args.length < 4) {
            lender.sendMessage(ChatColor.RED + "使い方: /debt lend <プレイヤー> <金額> <期限(分)>");
            return;
        }

        Player borrower = Bukkit.getPlayer(args[1]);
        if (borrower == null) {
            lender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[1]);
            return;
        }

        if (borrower.equals(lender)) {
            lender.sendMessage(ChatColor.RED + "自分自身には貸せません。");
            return;
        }

        double amount;
        int minutes;
        try {
            amount = Double.parseDouble(args[2]);
            minutes = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            lender.sendMessage(ChatColor.RED + "金額と期限は数値で入力してください。");
            return;
        }

        if (amount <= 0 || minutes <= 0) {
            lender.sendMessage(ChatColor.RED + "金額と期限は正の数で入力してください。");
            return;
        }

        // 残高チェック
        if (economy.getBalance(lender) < amount) {
            lender.sendMessage(ChatColor.RED + "残高が不足しています。");
            return;
        }

        // 金銭移動
        economy.withdrawPlayer(lender, amount);
        economy.depositPlayer(borrower, amount);

        // 債権登録
        long deadline = System.currentTimeMillis() + (minutes * 60 * 1000L);
        debtManager.addDebt(lender.getUniqueId(), borrower.getUniqueId(), amount, deadline);

        lender.sendMessage(ChatColor.GOLD + borrower.getName() + " に " +
                String.format("%.0f", amount) + " を貸しました（期限: " + minutes + "分）。");
        borrower.sendMessage(ChatColor.GOLD + lender.getName() + " から " +
                String.format("%.0f", amount) + " を借りました（期限: " + minutes + "分）。");
    }

    private void handleRepay(Player debtor, String[] args) {
        if (args.length < 3) {
            debtor.sendMessage(ChatColor.RED + "使い方: /debt repay <債権者> <金額>");
            return;
        }

        Player creditor = Bukkit.getPlayer(args[1]);
        if (creditor == null) {
            debtor.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[1]);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            debtor.sendMessage(ChatColor.RED + "金額は数値で入力してください。");
            return;
        }

        if (amount <= 0) {
            debtor.sendMessage(ChatColor.RED + "金額は正の数で入力してください。");
            return;
        }

        if (economy.getBalance(debtor) < amount) {
            debtor.sendMessage(ChatColor.RED + "残高が不足しています。");
            return;
        }

        double repaid = debtManager.repay(debtor.getUniqueId(), creditor.getUniqueId(), amount);
        if (repaid <= 0) {
            debtor.sendMessage(ChatColor.RED + creditor.getName() + " への債務がありません。");
            return;
        }

        economy.withdrawPlayer(debtor, repaid);
        economy.depositPlayer(creditor, repaid);

        debtor.sendMessage(ChatColor.GOLD + creditor.getName() + " に " +
                String.format("%.0f", repaid) + " を返済しました。");
        creditor.sendMessage(ChatColor.GOLD + debtor.getName() + " から " +
                String.format("%.0f", repaid) + " の返済を受けました。");
    }

    private void handleForgive(Player creditor, String[] args) {
        if (args.length < 2) {
            creditor.sendMessage(ChatColor.RED + "使い方: /debt forgive <債務者>");
            return;
        }

        Player debtor = Bukkit.getPlayer(args[1]);
        UUID debtorId;
        String debtorName;
        if (debtor != null) {
            debtorId = debtor.getUniqueId();
            debtorName = debtor.getName();
        } else {
            creditor.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[1]);
            return;
        }

        boolean forgiven = debtManager.forgive(creditor.getUniqueId(), debtorId);
        if (!forgiven) {
            creditor.sendMessage(ChatColor.RED + debtorName + " への債権がありません。");
            return;
        }

        creditor.sendMessage(ChatColor.GOLD + debtorName + " の債務を許しました。");
        if (debtor != null) {
            debtor.sendMessage(ChatColor.GOLD + creditor.getName() + " があなたの債務を許しました。");
        }
    }

    private void handleList(Player player) {
        List<DebtManager.Debt> playerDebts = debtManager.getDebtsForPlayer(player.getUniqueId());

        if (playerDebts.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "債権/債務はありません。");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== 債権/債務一覧 ===");
        for (DebtManager.Debt debt : playerDebts) {
            long remainingMs = debt.deadline - System.currentTimeMillis();
            String remaining = remainingMs > 0 ?
                    String.format("%d分", remainingMs / 60000) : "期限切れ";

            if (debt.creditor.equals(player.getUniqueId())) {
                String debtorName = Bukkit.getOfflinePlayer(debt.debtor).getName();
                player.sendMessage(ChatColor.GREEN + "  [貸] " + debtorName +
                        " へ " + String.format("%.0f", debt.amount) +
                        " (残り: " + remaining + ")");
            } else {
                String creditorName = Bukkit.getOfflinePlayer(debt.creditor).getName();
                player.sendMessage(ChatColor.RED + "  [借] " + creditorName +
                        " から " + String.format("%.0f", debt.amount) +
                        " (残り: " + remaining + ")");
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /debt lend <プレイヤー> <金額> <期限(分)> - お金を貸す");
        player.sendMessage(ChatColor.YELLOW + "  /debt repay <債権者> <金額> - 返済する");
        player.sendMessage(ChatColor.YELLOW + "  /debt forgive <債務者> - 債務を許す");
        player.sendMessage(ChatColor.YELLOW + "  /debt list - 一覧表示");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("lend", "repay", "forgive", "list"));
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
