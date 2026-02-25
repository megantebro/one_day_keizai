package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.StockDataManager;
import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.stock.StockOffer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * 投資株システムのコアロジック。
 * <ul>
 *   <li>株価 = QuickShop tax 口座残高 × 1%</li>
 *   <li>購入: 購入額を tax 口座に入金</li>
 *   <li>システム売却: tax 口座から株価を出金、手数料20%控除後にプレイヤーへ支払い (手数料もtaxへ)</li>
 *   <li>P2P 売却: 買い手→売り手へ直接支払い、手数料20%は tax へ</li>
 *   <li>配当: 1時間ごとに (株価 × 1%) × 保有数 を各株主へ tax から支払い</li>
 * </ul>
 */
public class StockManager {

    private static final String TAX_ACCOUNT = "tax";

    private final JavaPlugin plugin;
    private final Economy economy;
    private final JobManager jobManager;
    private final StockDataManager stockData;
    private final double sellFee;       // 例: 0.20
    private final double dividendRate;  // 例: 0.01 (株価の1%)
    private final int dividendIntervalMinutes;

    public StockManager(JavaPlugin plugin, Economy economy, JobManager jobManager,
                        StockDataManager stockData,
                        double sellFee, double dividendRate, int dividendIntervalMinutes) {
        this.plugin = plugin;
        this.economy = economy;
        this.jobManager = jobManager;
        this.stockData = stockData;
        this.sellFee = sellFee;
        this.dividendRate = dividendRate;
        this.dividendIntervalMinutes = dividendIntervalMinutes;
    }

    // ─── 配当スケジューラー ─────────────────────────────────────────────────

    public void startDividendScheduler() {
        long tickInterval = 20L * 60 * dividendIntervalMinutes;
        Bukkit.getScheduler().runTaskTimer(plugin, this::distributeDividends, tickInterval, tickInterval);
    }

    // ─── Tax 口座 ────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public OfflinePlayer getTaxPlayer() {
        return Bukkit.getOfflinePlayer(TAX_ACCOUNT);
    }

    public double getTaxBalance() {
        return economy.getBalance(getTaxPlayer());
    }

    /**
     * 現在の株価 = Tax残高 × 1%
     */
    public double getStockPrice() {
        return getTaxBalance() * 0.01;
    }

    // ─── 保有数 ──────────────────────────────────────────────────────────────

    public int getHoldings(UUID uuid) {
        return stockData.getHoldings(uuid);
    }

    public int getTotalShares() {
        return stockData.getTotalShares();
    }

    // ─── 購入 (システムから) ────────────────────────────────────────────────

    /**
     * プレイヤーがシステムから株を amount 口購入する。
     * 購入額は tax 口座に入金される。
     *
     * @return エラーメッセージ（null = 成功）
     */
    public String buyStock(Player player, int amount) {
        if (amount <= 0) return "購入口数は1以上を指定してください。";
        if (!jobManager.isCapitalist(player.getUniqueId()))
            return "株の購入は資本家のみ可能です。(/job promote で昇格)";

        double price = getStockPrice();
        if (price <= 0) return "現在 Tax 残高がないため株価が 0 です。取引できません。";

        double total = price * amount;
        if (economy.getBalance(player) < total)
            return String.format("残高不足です。必要: %s G (現在: %s G)",
                    fmt(total), fmt(economy.getBalance(player)));

        // プレイヤーから引く
        EconomyResponse r = economy.withdrawPlayer(player, total);
        if (!r.transactionSuccess()) return "支払い処理に失敗しました: " + r.errorMessage;

        // Tax に入金
        economy.depositPlayer(getTaxPlayer(), total);

        stockData.addHoldings(player.getUniqueId(), amount);
        stockData.save();

        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  株購入完了！");
        player.sendMessage(ChatColor.WHITE + "  " + amount + " 口 × " + fmt(price) + " G = " + fmt(total) + " G");
        player.sendMessage(ChatColor.GRAY  + "  保有数: " + stockData.getHoldings(player.getUniqueId()) + " 口");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return null;
    }

    // ─── 売却 (システムへ) ───────────────────────────────────────────────────

    /**
     * プレイヤーがシステムへ株を amount 口売却する。
     * 売価は現時点の Tax×1% 。手数料(20%)控除後に受け取り、手数料は Tax へ戻る。
     *
     * @return エラーメッセージ（null = 成功）
     */
    public String sellToSystem(Player player, int amount) {
        if (amount <= 0) return "売却口数は1以上を指定してください。";

        int held = stockData.getHoldings(player.getUniqueId());
        if (held < amount)
            return String.format("保有数が不足しています。(保有: %d 口, 売却要求: %d 口)", held, amount);

        double price = getStockPrice();
        if (price <= 0) return "現在 Tax 残高がないため株価が 0 です。取引できません。";

        double gross = price * amount;
        double fee = gross * sellFee;
        double net = gross - fee;

        double taxBalance = getTaxBalance();
        if (taxBalance < gross)
            return String.format("Tax 残高が不足しています。(Tax: %s G, 必要: %s G)",
                    fmt(taxBalance), fmt(gross));

        // Tax から引く
        EconomyResponse r = economy.withdrawPlayer(getTaxPlayer(), gross);
        if (!r.transactionSuccess()) return "Tax からの出金に失敗しました: " + r.errorMessage;

        // プレイヤーに net を支払い
        economy.depositPlayer(player, net);
        // 手数料は Tax に戻す
        economy.depositPlayer(getTaxPlayer(), fee);

        stockData.addHoldings(player.getUniqueId(), -amount);
        stockData.save();

        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  株売却完了！");
        player.sendMessage(ChatColor.WHITE + "  売却: " + amount + " 口 × " + fmt(price) + " G");
        player.sendMessage(ChatColor.WHITE + "  手数料: -" + fmt(fee) + " G (" + (int)(sellFee*100) + "%)");
        player.sendMessage(ChatColor.GREEN + "  受取: +" + fmt(net) + " G");
        player.sendMessage(ChatColor.GRAY  + "  残保有数: " + stockData.getHoldings(player.getUniqueId()) + " 口");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return null;
    }

    // ─── P2P オファー作成 ─────────────────────────────────────────────────

    /**
     * fromPlayer が toPlayer に amount 口を pricePerShare/株 で売るオファーを作成する。
     * pricePerShare <= 0 の場合、現在の市場価格を使用。
     *
     * @return エラーメッセージ（null = 成功）
     */
    public String createOffer(Player from, Player to, int amount, double pricePerShare) {
        if (amount <= 0) return "口数は1以上を指定してください。";
        if (from.equals(to)) return "自分自身へのオファーはできません。";

        int held = stockData.getHoldings(from.getUniqueId());
        if (held < amount)
            return String.format("保有数が不足しています。(保有: %d 口, オファー口数: %d 口)", held, amount);

        if (pricePerShare <= 0) pricePerShare = getStockPrice();

        // 同じ相手への既存オファーがあれば上書き
        Optional<StockOffer> existing = stockData.getOffer(from.getUniqueId(), to.getUniqueId());
        existing.ifPresent(o -> stockData.removeOffer(o.id));

        StockOffer offer = new StockOffer(from.getUniqueId(), to.getUniqueId(), amount, pricePerShare);
        stockData.addOffer(offer);
        stockData.save();

        double total = pricePerShare * amount;
        from.sendMessage(ChatColor.AQUA + to.getName() + " に " + amount + " 口 (計 "
                + fmt(total) + " G) のオファーを送りました。");

        if (to.isOnline()) {
            to.sendMessage(ChatColor.YELLOW + "[株オファー] " + from.getName()
                    + " から " + amount + " 口 (計 " + fmt(total) + " G / 手数料20%控除後: "
                    + fmt(total * (1 - sellFee)) + " G) のオファーが届きました。");
            to.sendMessage(ChatColor.YELLOW + "  /stock accept " + from.getName() + " で承諾、"
                    + "/stock reject " + from.getName() + " で拒否");
        }
        return null;
    }

    // ─── P2P オファー承諾 ─────────────────────────────────────────────────

    /**
     * buyer が fromUuid からのオファーを承諾して取引を完了する。
     *
     * @return エラーメッセージ（null = 成功）
     */
    public String acceptOffer(Player buyer, UUID fromUuid) {
        Optional<StockOffer> optOffer = stockData.getOffer(fromUuid, buyer.getUniqueId());
        if (optOffer.isEmpty())
            return "そのプレイヤーからのオファーが見つかりません。";

        StockOffer offer = optOffer.get();

        // 売り手の保有確認
        int sellerHeld = stockData.getHoldings(fromUuid);
        if (sellerHeld < offer.amount)
            return "売り手の保有数が不足しています。オファーをキャンセルしました。";

        double total = offer.price * offer.amount;
        double fee   = total * sellFee;
        double net   = total - fee;

        // 買い手の残高確認
        if (economy.getBalance(buyer) < total)
            return String.format("残高不足です。必要: %s G (現在: %s G)",
                    fmt(total), fmt(economy.getBalance(buyer)));

        // 取引実行
        economy.withdrawPlayer(buyer, total);        // 買い手 → 支払い
        economy.depositPlayer(getTaxPlayer(), fee);  // 手数料 → tax
        @SuppressWarnings("deprecation")
        OfflinePlayer seller = Bukkit.getOfflinePlayer(fromUuid);
        economy.depositPlayer(seller, net);          // 売り手 → net 受け取り

        // 保有数更新
        stockData.addHoldings(fromUuid, -offer.amount);
        stockData.addHoldings(buyer.getUniqueId(), offer.amount);
        stockData.removeOffer(offer.id);
        stockData.save();

        buyer.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        buyer.sendMessage(ChatColor.YELLOW + "  P2P 株取引完了！");
        buyer.sendMessage(ChatColor.WHITE  + "  " + offer.amount + " 口 × "
                + fmt(offer.price) + " G を " + Bukkit.getOfflinePlayer(fromUuid).getName() + " から購入");
        buyer.sendMessage(ChatColor.WHITE  + "  支払い: -" + fmt(total) + " G");
        buyer.sendMessage(ChatColor.GRAY   + "  保有数: " + stockData.getHoldings(buyer.getUniqueId()) + " 口");
        buyer.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (seller.isOnline()) {
            //noinspection ConstantConditions
            ((Player) seller.getPlayer()).sendMessage(
                    ChatColor.GREEN + buyer.getName() + " がオファーを承諾しました。+"
                            + fmt(net) + " G (手数料 -" + fmt(fee) + " G)");
        }
        return null;
    }

    // ─── P2P オファー拒否 ─────────────────────────────────────────────────

    /**
     * player が fromUuid からのオファーを拒否する。
     *
     * @return エラーメッセージ（null = 成功）
     */
    public String rejectOffer(Player player, UUID fromUuid) {
        Optional<StockOffer> optOffer = stockData.getOffer(fromUuid, player.getUniqueId());
        if (optOffer.isEmpty())
            return "そのプレイヤーからのオファーが見つかりません。";

        stockData.removeOffer(optOffer.get().id);
        stockData.save();

        player.sendMessage(ChatColor.YELLOW + "オファーを拒否しました。");

        @SuppressWarnings("deprecation")
        OfflinePlayer from = Bukkit.getOfflinePlayer(fromUuid);
        if (from.isOnline()) {
            //noinspection ConstantConditions
            ((Player) from.getPlayer()).sendMessage(
                    ChatColor.RED + player.getName() + " がオファーを拒否しました。");
        }
        return null;
    }

    // ─── 配当分配 ────────────────────────────────────────────────────────────

    /**
     * 全株主に (株価 × dividendRate) × 保有数 を tax 口座から支払う。
     * Tax 残高不足の場合は残高内で比例分配する。
     */
    public void distributeDividends() {
        Map<UUID, Integer> holdings = stockData.getAllHoldings();
        if (holdings.isEmpty()) return;

        double price = getStockPrice();
        if (price <= 0) return;

        double perShare = price * dividendRate;

        // 支払い総額を計算
        double totalPayout = 0;
        for (int shares : holdings.values()) totalPayout += perShare * shares;

        double taxBalance = getTaxBalance();
        if (taxBalance <= 0) return;

        // tax 残高が足りない場合はスケール
        double scale = Math.min(1.0, taxBalance / totalPayout);

        StringBuilder log = new StringBuilder();
        log.append(ChatColor.GOLD).append("【株配当】1時間配当 (株価: ")
                .append(fmt(price)).append(" G / 配当率: ").append((int)(dividendRate*100)).append("%)\n");

        for (Map.Entry<UUID, Integer> entry : holdings.entrySet()) {
            UUID uuid = entry.getKey();
            int shares = entry.getValue();
            double payout = perShare * shares * scale;
            if (payout <= 0) continue;

            EconomyResponse r = economy.withdrawPlayer(getTaxPlayer(), payout);
            if (!r.transactionSuccess()) continue;

            @SuppressWarnings("deprecation")
            OfflinePlayer holder = Bukkit.getOfflinePlayer(uuid);
            economy.depositPlayer(holder, payout);

            log.append(ChatColor.WHITE).append("  ")
                    .append(holder.getName() != null ? holder.getName() : uuid.toString())
                    .append(": +").append(fmt(payout)).append(" G (").append(shares).append(" 口)\n");

            if (holder.isOnline()) {
                //noinspection ConstantConditions
                ((Player) holder.getPlayer()).sendMessage(
                        ChatColor.GOLD + "[株配当] +" + fmt(payout) + " G ("
                                + shares + " 口 × " + fmt(perShare * scale) + " G)");
            }
        }

        // 全員に配当のブロードキャスト
        Bukkit.broadcastMessage(log.toString().trim());
    }

    // ─── ユーティリティ ──────────────────────────────────────────────────────

    public StockDataManager getStockData() {
        return stockData;
    }

    public double getSellFee() {
        return sellFee;
    }

    public static String fmt(double v) {
        return NumberFormat.getNumberInstance(Locale.JAPAN).format((long) v);
    }
}
