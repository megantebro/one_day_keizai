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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DebtCommand implements CommandExecutor, TabCompleter {

    private static final double MAX_INTEREST_RATE = 50.0;
    private static final int MIN_DURATION_MINUTES = 20;

    private final DebtManager debtManager;
    private final Economy economy;
    private final Map<UUID, LendRequest> pendingRequests = new ConcurrentHashMap<>();

    public static class LendRequest {
        public final UUID lender;
        public final UUID borrower;
        public final double amount;
        public final int minutes;
        public final double interestRate;
        public final long createdAt;

        public LendRequest(UUID lender, UUID borrower, double amount, int minutes, double interestRate) {
            this.lender = lender;
            this.borrower = borrower;
            this.amount = amount;
            this.minutes = minutes;
            this.interestRate = interestRate;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public DebtCommand(DebtManager debtManager, Economy economy) {
        this.debtManager = debtManager;
        this.economy = economy;
    }

    // テスト用
    Map<UUID, LendRequest> getPendingRequests() {
        return pendingRequests;
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
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "repay" -> handleRepay(player, args);
            case "forgive" -> handleForgive(player, args);
            case "list" -> handleList(player);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleLend(Player lender, String[] args) {
        if (args.length < 4) {
            lender.sendMessage(ChatColor.RED + "使い方: /debt lend <プレイヤー> <金額> <期限(分)> [利率%]");
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
        double interestRate = 0;
        try {
            amount = Double.parseDouble(args[2]);
            minutes = Integer.parseInt(args[3]);
            if (args.length >= 5) {
                interestRate = Double.parseDouble(args[4]);
            }
        } catch (NumberFormatException e) {
            lender.sendMessage(ChatColor.RED + "金額・期限・利率は数値で入力してください。");
            return;
        }

        if (amount <= 0 || minutes <= 0) {
            lender.sendMessage(ChatColor.RED + "金額と期限は正の数で入力してください。");
            return;
        }

        if (minutes < MIN_DURATION_MINUTES) {
            lender.sendMessage(ChatColor.RED + "期限は" + MIN_DURATION_MINUTES + "分以上で設定してください。");
            return;
        }

        if (interestRate < 0) {
            lender.sendMessage(ChatColor.RED + "利率は0以上で入力してください。");
            return;
        }

        if (interestRate > MAX_INTEREST_RATE) {
            lender.sendMessage(ChatColor.RED + "利率は" + String.format("%.0f", MAX_INTEREST_RATE) + "%以下で設定してください。");
            return;
        }

        // 残高チェック
        if (economy.getBalance(lender) < amount) {
            lender.sendMessage(ChatColor.RED + "残高が不足しています。");
            return;
        }

        // 既にリクエストが保留中か確認
        if (pendingRequests.containsKey(borrower.getUniqueId())) {
            lender.sendMessage(ChatColor.RED + "そのプレイヤーには既に貸付リクエストが保留中です。");
            return;
        }

        // リクエストを作成（即時実行しない）
        LendRequest request = new LendRequest(
                lender.getUniqueId(), borrower.getUniqueId(), amount, minutes, interestRate);
        pendingRequests.put(borrower.getUniqueId(), request);

        double totalRepayment = amount * (1 + interestRate / 100.0);
        String interestInfo = interestRate > 0 ?
                "、利率: " + String.format("%.1f", interestRate) + "%、返済額: " + String.format("%.0f", totalRepayment) : "";

        lender.sendMessage(ChatColor.GOLD + borrower.getName() + " に貸付リクエストを送りました（" +
                String.format("%.0f", amount) + "、期限: " + minutes + "分" + interestInfo + "）。");
        borrower.sendMessage(ChatColor.GOLD + "=== 貸付リクエスト ===");
        borrower.sendMessage(ChatColor.YELLOW + lender.getName() + " から " +
                String.format("%.0f", amount) + " の貸付申請（期限: " + minutes + "分" + interestInfo + "）");
        borrower.sendMessage(ChatColor.GREEN + "  /debt accept" + ChatColor.YELLOW + " で承認、" +
                ChatColor.RED + "/debt deny" + ChatColor.YELLOW + " で拒否");
    }

    private void handleAccept(Player borrower) {
        LendRequest request = pendingRequests.remove(borrower.getUniqueId());
        if (request == null) {
            borrower.sendMessage(ChatColor.RED + "保留中の貸付リクエストがありません。");
            return;
        }

        // 60秒以上経過したリクエストは期限切れ
        if (System.currentTimeMillis() - request.createdAt > 60_000) {
            borrower.sendMessage(ChatColor.RED + "貸付リクエストの有効期限が切れました。");
            return;
        }

        Player lender = Bukkit.getPlayer(request.lender);
        if (lender == null) {
            borrower.sendMessage(ChatColor.RED + "貸し手がオフラインです。");
            return;
        }

        // 残高再チェック
        if (economy.getBalance(lender) < request.amount) {
            borrower.sendMessage(ChatColor.RED + "貸し手の残高が不足しています。");
            lender.sendMessage(ChatColor.RED + "残高不足のため貸付が実行できませんでした。");
            return;
        }

        // 金銭移動
        economy.withdrawPlayer(lender, request.amount);
        economy.depositPlayer(borrower, request.amount);

        // 債権登録
        long deadline = System.currentTimeMillis() + (request.minutes * 60 * 1000L);
        debtManager.addDebt(lender.getUniqueId(), borrower.getUniqueId(),
                request.amount, deadline, request.interestRate);

        double totalRepayment = request.amount * (1 + request.interestRate / 100.0);
        String interestInfo = request.interestRate > 0 ?
                "、利率: " + String.format("%.1f", request.interestRate) + "%、返済額: " + String.format("%.0f", totalRepayment) : "";

        lender.sendMessage(ChatColor.GOLD + borrower.getName() + " が貸付を承認しました（" +
                String.format("%.0f", request.amount) + "、期限: " + request.minutes + "分" + interestInfo + "）。");
        borrower.sendMessage(ChatColor.GOLD + lender.getName() + " から " +
                String.format("%.0f", request.amount) + " を借りました（期限: " + request.minutes + "分" + interestInfo + "）。");
    }

    private void handleDeny(Player borrower) {
        LendRequest request = pendingRequests.remove(borrower.getUniqueId());
        if (request == null) {
            borrower.sendMessage(ChatColor.RED + "保留中の貸付リクエストがありません。");
            return;
        }

        Player lender = Bukkit.getPlayer(request.lender);
        borrower.sendMessage(ChatColor.GOLD + "貸付リクエストを拒否しました。");
        if (lender != null) {
            lender.sendMessage(ChatColor.RED + borrower.getName() + " が貸付リクエストを拒否しました。");
        }
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

            String interestInfo = debt.interestRate > 0 ?
                    " 利率:" + String.format("%.1f", debt.interestRate) + "%" : "";

            if (debt.creditor.equals(player.getUniqueId())) {
                String debtorName = Bukkit.getOfflinePlayer(debt.debtor).getName();
                player.sendMessage(ChatColor.GREEN + "  [貸] " + debtorName +
                        " へ " + String.format("%.0f", debt.amount) +
                        interestInfo + " (残り: " + remaining + ")");
            } else {
                String creditorName = Bukkit.getOfflinePlayer(debt.creditor).getName();
                player.sendMessage(ChatColor.RED + "  [借] " + creditorName +
                        " から " + String.format("%.0f", debt.amount) +
                        interestInfo + " (残り: " + remaining + ")");
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /debt lend <プレイヤー> <金額> <期限(分)> [利率%] - 貸付リクエスト（期限" + MIN_DURATION_MINUTES + "分以上、利率" + String.format("%.0f", MAX_INTEREST_RATE) + "%以下）");
        player.sendMessage(ChatColor.YELLOW + "  /debt accept - 貸付リクエストを承認");
        player.sendMessage(ChatColor.YELLOW + "  /debt deny - 貸付リクエストを拒否");
        player.sendMessage(ChatColor.YELLOW + "  /debt repay <債権者> <金額> - 返済する");
        player.sendMessage(ChatColor.YELLOW + "  /debt forgive <債務者> - 債務を許す");
        player.sendMessage(ChatColor.YELLOW + "  /debt list - 一覧表示");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(Arrays.asList("lend", "accept", "deny", "repay", "forgive", "list"));
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
