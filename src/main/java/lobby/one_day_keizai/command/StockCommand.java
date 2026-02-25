package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.StockManager;
import lobby.one_day_keizai.stock.StockOffer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /stock コマンド。
 *
 * <ul>
 *   <li>/stock info                         — 株価・保有数・Tax残高を表示</li>
 *   <li>/stock buy &lt;口数&gt;             — システムから購入</li>
 *   <li>/stock sell &lt;口数&gt;            — システムへ売却 (手数料20%)</li>
 *   <li>/stock offer &lt;player&gt; &lt;口数&gt; [価格/株] — P2P 売却オファー</li>
 *   <li>/stock accept [player]              — P2P オファーを承諾</li>
 *   <li>/stock reject [player]              — P2P オファーを拒否</li>
 * </ul>
 */
public class StockCommand implements CommandExecutor, TabCompleter {

    private final StockManager stockManager;

    public StockCommand(StockManager stockManager) {
        this.stockManager = stockManager;
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
            case "info"   -> handleInfo(player);
            case "buy"    -> handleBuy(player, args);
            case "sell"   -> handleSell(player, args);
            case "offer"  -> handleOffer(player, args);
            case "accept" -> handleAccept(player, args);
            case "reject" -> handleReject(player, args);
            default       -> sendUsage(player);
        }
        return true;
    }

    // ─── info ─────────────────────────────────────────────────────────────────

    private void handleInfo(Player player) {
        double tax    = stockManager.getTaxBalance();
        double price  = stockManager.getStockPrice();
        int myShares  = stockManager.getHoldings(player.getUniqueId());
        int total     = stockManager.getTotalShares();
        double perDiv = price * stockManager.getSellFee() / 5; // divRateは private なので近似

        // 受取配当予測 (1h)
        double divPerShare = price * 0.01;

        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━ 株式情報 ━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  現在株価:   " + ChatColor.WHITE + StockManager.fmt(price) + " G");
        player.sendMessage(ChatColor.YELLOW + "  Tax 残高:   " + ChatColor.WHITE + StockManager.fmt(tax) + " G");
        player.sendMessage(ChatColor.YELLOW + "  総発行株数: " + ChatColor.WHITE + total + " 口");
        player.sendMessage(ChatColor.GOLD   + "---");
        player.sendMessage(ChatColor.YELLOW + "  あなたの保有: " + ChatColor.WHITE + myShares + " 口");
        if (myShares > 0) {
            player.sendMessage(ChatColor.YELLOW + "  保有評価額: " + ChatColor.WHITE
                    + StockManager.fmt(price * myShares) + " G");
            player.sendMessage(ChatColor.YELLOW + "  予想配当(1h): " + ChatColor.WHITE
                    + StockManager.fmt(divPerShare * myShares) + " G");
        }

        // 受信オファー
        List<StockOffer> incomingOffers = stockManager.getStockData().getOffersTo(player.getUniqueId());
        if (!incomingOffers.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "--- 受信オファー ---");
            for (StockOffer o : incomingOffers) {
                @SuppressWarnings("deprecation")
                String fromName = Bukkit.getOfflinePlayer(o.fromUuid).getName();
                player.sendMessage(ChatColor.AQUA + "  " + fromName + " から " + o.amount
                        + " 口 × " + StockManager.fmt(o.price) + " G"
                        + ChatColor.GRAY + " → /stock accept " + fromName);
            }
        }

        // 送信オファー
        List<StockOffer> sentOffers = stockManager.getStockData().getOffersFrom(player.getUniqueId());
        if (!sentOffers.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "--- 送信中オファー ---");
            for (StockOffer o : sentOffers) {
                @SuppressWarnings("deprecation")
                String toName = Bukkit.getOfflinePlayer(o.toUuid).getName();
                player.sendMessage(ChatColor.GRAY + "  " + toName + " へ " + o.amount
                        + " 口 × " + StockManager.fmt(o.price) + " G (未承認)");
            }
        }

        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ─── buy ──────────────────────────────────────────────────────────────────

    private void handleBuy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /stock buy <口数>");
            return;
        }
        int amount = parsePositiveInt(player, args[1]);
        if (amount <= 0) return;

        String err = stockManager.buyStock(player, amount);
        if (err != null) player.sendMessage(ChatColor.RED + err);
    }

    // ─── sell ─────────────────────────────────────────────────────────────────

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /stock sell <口数>");
            return;
        }
        int amount = parsePositiveInt(player, args[1]);
        if (amount <= 0) return;

        String err = stockManager.sellToSystem(player, amount);
        if (err != null) player.sendMessage(ChatColor.RED + err);
    }

    // ─── offer ────────────────────────────────────────────────────────────────

    private void handleOffer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "使い方: /stock offer <player> <口数> [価格/株]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "プレイヤー " + args[1] + " はオンラインではありません。");
            return;
        }

        int amount = parsePositiveInt(player, args[2]);
        if (amount <= 0) return;

        double pricePerShare = 0;
        if (args.length >= 4) {
            try {
                pricePerShare = Double.parseDouble(args[3]);
                if (pricePerShare < 0) {
                    player.sendMessage(ChatColor.RED + "価格は0以上を指定してください。");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "価格の形式が正しくありません: " + args[3]);
                return;
            }
        }

        String err = stockManager.createOffer(player, target, amount, pricePerShare);
        if (err != null) player.sendMessage(ChatColor.RED + err);
    }

    // ─── accept ───────────────────────────────────────────────────────────────

    private void handleAccept(Player player, String[] args) {
        List<StockOffer> incoming = stockManager.getStockData().getOffersTo(player.getUniqueId());

        if (incoming.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "受信しているオファーはありません。");
            return;
        }

        UUID fromUuid;
        if (args.length >= 2) {
            // 名前指定
            fromUuid = resolveUuidByName(args[1]);
            if (fromUuid == null) {
                player.sendMessage(ChatColor.RED + "プレイヤー " + args[1] + " が見つかりません。");
                return;
            }
        } else if (incoming.size() == 1) {
            fromUuid = incoming.get(0).fromUuid;
        } else {
            player.sendMessage(ChatColor.RED + "複数のオファーがあります。/stock accept <player> で指定してください。");
            for (StockOffer o : incoming) {
                @SuppressWarnings("deprecation")
                String name = Bukkit.getOfflinePlayer(o.fromUuid).getName();
                player.sendMessage(ChatColor.GRAY + "  - " + name + " (" + o.amount + " 口)");
            }
            return;
        }

        String err = stockManager.acceptOffer(player, fromUuid);
        if (err != null) player.sendMessage(ChatColor.RED + err);
    }

    // ─── reject ───────────────────────────────────────────────────────────────

    private void handleReject(Player player, String[] args) {
        List<StockOffer> incoming = stockManager.getStockData().getOffersTo(player.getUniqueId());

        if (incoming.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "受信しているオファーはありません。");
            return;
        }

        UUID fromUuid;
        if (args.length >= 2) {
            fromUuid = resolveUuidByName(args[1]);
            if (fromUuid == null) {
                player.sendMessage(ChatColor.RED + "プレイヤー " + args[1] + " が見つかりません。");
                return;
            }
        } else if (incoming.size() == 1) {
            fromUuid = incoming.get(0).fromUuid;
        } else {
            player.sendMessage(ChatColor.RED + "複数のオファーがあります。/stock reject <player> で指定してください。");
            return;
        }

        String err = stockManager.rejectOffer(player, fromUuid);
        if (err != null) player.sendMessage(ChatColor.RED + err);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private int parsePositiveInt(Player player, String s) {
        try {
            int v = Integer.parseInt(s);
            if (v <= 0) {
                player.sendMessage(ChatColor.RED + "口数は1以上の整数を指定してください。");
                return 0;
            }
            return v;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "口数の形式が正しくありません: " + s);
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private UUID resolveUuidByName(String name) {
        // オンラインプレイヤー優先
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        // オフラインキャッシュ
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /stock info                         - 株価・保有状況を表示");
        player.sendMessage(ChatColor.YELLOW + "  /stock buy <口数>                   - 株を購入 (資本家限定)");
        player.sendMessage(ChatColor.YELLOW + "  /stock sell <口数>                  - 株をシステムに売却 (手数料20%)");
        player.sendMessage(ChatColor.YELLOW + "  /stock offer <player> <口数> [価格] - P2P 売却オファー");
        player.sendMessage(ChatColor.YELLOW + "  /stock accept [player]               - オファーを承諾");
        player.sendMessage(ChatColor.YELLOW + "  /stock reject [player]               - オファーを拒否");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("info", "buy", "sell", "offer", "accept", "reject");
            List<String> result = new ArrayList<>(subs);
            result.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return result;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("offer") || sub.equals("accept") || sub.equals("reject")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                        names.add(p.getName());
                }
                return names;
            }
        }
        return new ArrayList<>();
    }
}
