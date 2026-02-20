package lobby.one_day_keizai.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AuctionManager {

    private final JavaPlugin plugin;
    private final Economy economy;
    private final int auctionIntervalMinutes;
    private final int auctionDurationSeconds;

    private ItemStack currentItem;
    private String currentItemName;
    private final Map<UUID, Double> bids = new HashMap<>();
    private boolean auctionActive = false;

    private final List<AuctionItem> itemPool = new ArrayList<>();
    private final Random random = new Random();

    static class AuctionItem {
        final ItemStack item;
        final String name;

        AuctionItem(ItemStack item, String name) {
            this.item = item;
            this.name = name;
        }
    }

    public AuctionManager(JavaPlugin plugin, Economy economy,
                          int auctionIntervalMinutes, int auctionDurationSeconds) {
        this.plugin = plugin;
        this.economy = economy;
        this.auctionIntervalMinutes = auctionIntervalMinutes;
        this.auctionDurationSeconds = auctionDurationSeconds;
        initializeItemPool();
    }

    private void initializeItemPool() {
        // 1. エンチャント瓶 × 64
        itemPool.add(new AuctionItem(
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64), "エンチャント瓶 x64"));

        // 2. サドル × 1
        itemPool.add(new AuctionItem(
                new ItemStack(Material.SADDLE, 1), "サドル"));

        // 3. ダイヤブロック × 2
        itemPool.add(new AuctionItem(
                new ItemStack(Material.DIAMOND_BLOCK, 2), "ダイヤブロック x2"));

        // 4. 幸運IIのエンチャント本
        ItemStack fortuneBook = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) fortuneBook.getItemMeta();
        meta.addStoredEnchant(Enchantment.LOOT_BONUS_BLOCKS, 2, true);
        fortuneBook.setItemMeta(meta);
        itemPool.add(new AuctionItem(fortuneBook, "幸運IIのエンチャント本"));

        // 5. エンチャント瓶 × 128 (2スタック)
        itemPool.add(new AuctionItem(
                new ItemStack(Material.EXPERIENCE_BOTTLE, 128), "エンチャント瓶 x128"));
    }

    /**
     * 30分ごとにオークション開始をスケジュールする。
     */
    public void startAuctionScheduler() {
        long intervalTicks = 20L * 60 * auctionIntervalMinutes;
        Bukkit.getScheduler().runTaskTimer(plugin, this::startAuction, intervalTicks, intervalTicks);
    }

    /**
     * オークションを開始する。
     */
    public void startAuction() {
        if (auctionActive) return;

        AuctionItem selected = itemPool.get(random.nextInt(itemPool.size()));
        currentItem = selected.item.clone();
        currentItemName = selected.name;
        bids.clear();
        auctionActive = true;

        Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  オークション開始！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  アイテム: " + ChatColor.WHITE + currentItemName);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  入札: " + ChatColor.WHITE + "/auction <金額>");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  残り: " + ChatColor.WHITE + auctionDurationSeconds + "秒");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");

        // 終了をスケジュール
        long durationTicks = 20L * auctionDurationSeconds;
        Bukkit.getScheduler().runTaskLater(plugin, this::endAuction, durationTicks);
    }

    /**
     * 入札処理。
     */
    public void placeBid(Player player, double amount) {
        if (!auctionActive) {
            player.sendMessage(ChatColor.RED + "現在オークションは開催されていません。");
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "入札額は正の数で入力してください。");
            return;
        }

        if (economy.getBalance(player) < amount) {
            player.sendMessage(ChatColor.RED + "残高が不足しています。");
            return;
        }

        UUID playerId = player.getUniqueId();
        Double previousBid = bids.get(playerId);
        if (previousBid != null && amount <= previousBid) {
            player.sendMessage(ChatColor.RED + "前回の入札額（" +
                    String.format("%.0f", previousBid) + "円）より高い金額を入力してください。");
            return;
        }

        // 現在の最高額より高いかチェック
        double currentHighest = getHighestBid();
        if (amount <= currentHighest) {
            player.sendMessage(ChatColor.RED + "現在の最高入札額（" +
                    String.format("%.0f", currentHighest) + "円）より高い金額を入力してください。");
            return;
        }

        bids.put(playerId, amount);
        Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + " が " +
                String.format("%.0f", amount) + "円で入札！");
    }

    /**
     * オークション終了処理。
     */
    public void endAuction() {
        if (!auctionActive) return;
        auctionActive = false;

        if (bids.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "  オークション終了 — 入札者なし");
            Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
            currentItem = null;
            currentItemName = null;
            return;
        }

        // 最高額入札者を決定
        UUID winnerId = null;
        double highestBid = 0;
        for (Map.Entry<UUID, Double> entry : bids.entrySet()) {
            if (entry.getValue() > highestBid) {
                highestBid = entry.getValue();
                winnerId = entry.getKey();
            }
        }

        Player winner = Bukkit.getPlayer(winnerId);
        String winnerName = winner != null ? winner.getName() :
                Bukkit.getOfflinePlayer(winnerId).getName();

        // お金を引き落とし
        economy.withdrawPlayer(Bukkit.getOfflinePlayer(winnerId), highestBid);

        // アイテム付与
        if (winner != null && winner.isOnline()) {
            giveItem(winner, currentItem);
            winner.sendMessage(ChatColor.GREEN + "オークションで " + currentItemName +
                    " を " + String.format("%.0f", highestBid) + "円で落札しました！");
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  オークション終了！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  落札者: " + ChatColor.WHITE + winnerName);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  アイテム: " + ChatColor.WHITE + currentItemName);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  落札額: " + ChatColor.WHITE +
                String.format("%.0f", highestBid) + "円");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");

        currentItem = null;
        currentItemName = null;
    }

    private void giveItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
            player.sendMessage(ChatColor.YELLOW + "インベントリが満杯のため、一部のアイテムが足元にドロップされました。");
        }
    }

    public boolean isAuctionActive() {
        return auctionActive;
    }

    public String getCurrentItemName() {
        return currentItemName;
    }

    public double getHighestBid() {
        return bids.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    public Map<UUID, Double> getBids() {
        return Collections.unmodifiableMap(bids);
    }

    // テスト用: アイテムプールにアクセス
    List<AuctionItem> getItemPool() {
        return itemPool;
    }
}
