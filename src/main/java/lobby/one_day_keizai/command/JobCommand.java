package lobby.one_day_keizai.command;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.NametagManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final JobManager jobManager;
    private final Economy economy;
    private final NametagManager nametagManager;
    private final double promoteFee;
    private final double shopFee;
    private final Location wealthyMerchantShop;

    /**
     * @param wealthyMerchantShop 豪商ショップのTP先。null の場合はコマンド使用不可。
     */
    public JobCommand(JobManager jobManager, Economy economy, NametagManager nametagManager,
                      double promoteFee, double shopFee,
                      Location wealthyMerchantShop) {
        this.jobManager = jobManager;
        this.economy = economy;
        this.nametagManager = nametagManager;
        this.promoteFee = promoteFee;
        this.shopFee = shopFee;
        this.wealthyMerchantShop = wealthyMerchantShop;
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
            case "select"  -> handleSelect(player, args);
            case "info"    -> handleInfo(player);
            case "list"    -> handleList(player);
            case "promote" -> handlePromote(player);
            case "shop"    -> handleShop(player);
            default        -> sendUsage(player);
        }

        return true;
    }

    // ─── select ────────────────────────────────────────────────────────────────

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /job select <farmer|blacksmith|merchant>");
            return;
        }

        Job job = Job.fromString(args[1]);
        if (job == null || job == Job.NONE || job.isUpperTier()) {
            player.sendMessage(ChatColor.RED + "職業名が正しくありません。farmer / blacksmith / merchant のいずれかを指定してください。");
            return;
        }

        Job current = jobManager.getJob(player.getUniqueId());
        if (current == job) {
            player.sendMessage(ChatColor.YELLOW + "すでに " + job.getColorCode() + job.getDisplayName() + ChatColor.YELLOW + " です。");
            return;
        }

        // 上級職から基本職への変更は不可
        if (current.isUpperTier()) {
            player.sendMessage(ChatColor.RED + "上級職から基本職への変更はできません。");
            return;
        }

        jobManager.setJob(player.getUniqueId(), job);
        nametagManager.refreshJob(player);
        player.sendMessage(ChatColor.GREEN + "職業を " + job.getColorCode() + job.getDisplayName() + ChatColor.GREEN + " に変更しました。");
        sendJobDescription(player, job);
    }

    // ─── promote ───────────────────────────────────────────────────────────────

    private void handlePromote(Player player) {
        Job current = jobManager.getJob(player.getUniqueId());

        if (current == Job.NONE) {
            player.sendMessage(ChatColor.RED + "職業を選択してから昇格してください。");
            return;
        }
        if (current.isUpperTier()) {
            player.sendMessage(ChatColor.YELLOW + "すでに上級職 " + current.getColorCode() + current.getDisplayName()
                    + ChatColor.YELLOW + " です。");
            return;
        }

        Job upper = current.getUpperJob();
        if (upper == null) {
            player.sendMessage(ChatColor.RED + "この職業には上級職が存在しません。");
            return;
        }

        double balance = economy.getBalance(player);
        if (balance < promoteFee) {
            player.sendMessage(ChatColor.RED + String.format("昇格には %s G 必要です。現在の残高: %s G",
                    formatMoney(promoteFee), formatMoney(balance)));
            return;
        }

        EconomyResponse resp = economy.withdrawPlayer(player, promoteFee);
        if (!resp.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "支払い処理に失敗しました: " + resp.errorMessage);
            return;
        }

        jobManager.setJob(player.getUniqueId(), upper);
        nametagManager.refreshJob(player);
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  上級職に昇格しました！");
        player.sendMessage(ChatColor.WHITE + "  " + current.getColorCode() + current.getDisplayName()
                + ChatColor.WHITE + " → " + upper.getColorCode() + upper.getDisplayName());
        player.sendMessage(ChatColor.GRAY + "  費用: -" + formatMoney(promoteFee) + " G");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendJobDescription(player, upper);
    }

    // ─── shop ──────────────────────────────────────────────────────────────────

    private void handleShop(Player player) {
        if (!jobManager.isWealthyMerchant(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "このコマンドは豪商のみ使用できます。");
            return;
        }

        if (wealthyMerchantShop == null) {
            player.sendMessage(ChatColor.RED + "豪商ショップの座標がまだ設定されていません。管理者に連絡してください。");
            return;
        }

        double balance = economy.getBalance(player);
        if (balance < shopFee) {
            player.sendMessage(ChatColor.RED + String.format("豪商ショップへの転送には %s G 必要です。残高: %s G",
                    formatMoney(shopFee), formatMoney(balance)));
            return;
        }

        EconomyResponse resp = economy.withdrawPlayer(player, shopFee);
        if (!resp.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "支払い処理に失敗しました: " + resp.errorMessage);
            return;
        }

        player.sendMessage(ChatColor.AQUA + "豪商専用ショップへ転送します... (-" + formatMoney(shopFee) + " G)");
        player.teleport(wealthyMerchantShop);
    }

    // ─── info / list ───────────────────────────────────────────────────────────

    private void handleInfo(Player player) {
        Job job = jobManager.getJob(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "=== あなたの職業 ===");
        player.sendMessage(ChatColor.YELLOW + "職業: " + job.getColorCode() + job.getDisplayName()
                + (job.isUpperTier() ? ChatColor.GOLD + " ★上級職★" : ""));
        sendJobDescription(player, job);

        if (!job.isUpperTier() && job != Job.NONE) {
            Job upper = job.getUpperJob();
            if (upper != null) {
                player.sendMessage(ChatColor.GRAY + "  上級職: " + upper.getColorCode() + upper.getDisplayName()
                        + ChatColor.GRAY + " (昇格費用: " + formatMoney(promoteFee) + " G) → /job promote");
            }
        }
    }

    private void handleList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 職業一覧 ===");
        player.sendMessage(ChatColor.GRAY + "【基本職】");
        for (Job job : new Job[]{Job.FARMER, Job.BLACKSMITH, Job.MERCHANT}) {
            player.sendMessage(job.getColorCode() + "  " + job.getDisplayName()
                    + ChatColor.WHITE + " - " + getJobSummary(job));
        }
        player.sendMessage(ChatColor.GRAY + "【上級職】 (鍛冶屋が /job promote で昇格, 費用: " + formatMoney(promoteFee) + " G)");
        player.sendMessage(Job.ENCHANTER.getColorCode() + "  " + Job.ENCHANTER.getDisplayName()
                + ChatColor.GRAY + " ← " + Job.BLACKSMITH.getDisplayName()
                + ChatColor.WHITE + " - " + getJobSummary(Job.ENCHANTER));
        player.sendMessage(ChatColor.YELLOW + "/job select <farmer|blacksmith|merchant> で職業を選択");
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void sendJobDescription(Player player, Job job) {
        switch (job) {
            case FARMER -> {
                player.sendMessage(ChatColor.GREEN + "  農家の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 安全ワールドで農作物を栽培できます");
            }
            case BLACKSMITH -> {
                player.sendMessage(ChatColor.GOLD + "  鍛冶屋の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 鉄・金・ダイヤ・ネザライトのツール/装備をクラフトできます");
            }
            case MERCHANT -> {
                player.sendMessage(ChatColor.AQUA + "  商人の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 危険ワールドの遠方ショップにアクセスできます");
            }
            case CAPITALIST -> {
                player.sendMessage(ChatColor.GREEN + "  資本家の特権 (農家の全特権 + ):");
                player.sendMessage(ChatColor.WHITE + "  - 投資商材の売買ができます（準備中）");
            }
            case ENCHANTER -> {
                player.sendMessage(ChatColor.YELLOW + "  エンチャンターの特権 (鍛冶屋の全特権 + ):");
                player.sendMessage(ChatColor.WHITE + "  - エンチャントテーブルを使用できます");
            }
            case WEALTHY_MERCHANT -> {
                player.sendMessage(ChatColor.AQUA + "  豪商の特権 (商人の全特権 + ):");
                player.sendMessage(ChatColor.WHITE + "  - 豪商専用ショップにアクセスできます (/job shop, " + formatMoney(shopFee) + " G)");
                player.sendMessage(ChatColor.WHITE + "  - ロバのチェスト容量が 3 倍 (45スロット)");
            }
            case NONE -> player.sendMessage(ChatColor.GRAY + "  無職: 石ツール以下のクラフトのみ可能です。");
        }
    }

    private String getJobSummary(Job job) {
        return switch (job) {
            case FARMER          -> "安全ワールドで農作物を栽培できる";
            case BLACKSMITH      -> "鉄以上のツール・装備をクラフトできる";
            case MERCHANT        -> "遠方ショップにアクセスできる";
            case CAPITALIST      -> "投資商材の売買ができる（準備中）";
            case ENCHANTER       -> "エンチャントテーブルが使える";
            case WEALTHY_MERCHANT -> "豪商専用ショップ＆ロバ3倍チェスト";
            default              -> "";
        };
    }

    private String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(Locale.JAPAN).format((long) amount);
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /job select <farmer|blacksmith|merchant>  - 職業を選択");
        player.sendMessage(ChatColor.YELLOW + "  /job promote                               - 上級職に昇格 (" + formatMoney(promoteFee) + " G)");
        player.sendMessage(ChatColor.YELLOW + "  /job shop                                  - 豪商専用ショップへTP (" + formatMoney(shopFee) + " G)");
        player.sendMessage(ChatColor.YELLOW + "  /job info                                  - 現在の職業を確認");
        player.sendMessage(ChatColor.YELLOW + "  /job list                                  - 職業一覧を表示");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> cmds = Arrays.asList("select", "promote", "shop", "info", "list");
            List<String> result = new ArrayList<>(cmds);
            result.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            List<String> jobs = Arrays.asList("farmer", "blacksmith", "merchant");
            List<String> result = new ArrayList<>(jobs);
            result.removeIf(s -> !s.startsWith(args[1].toLowerCase()));
            return result;
        }
        return new ArrayList<>();
    }
}
