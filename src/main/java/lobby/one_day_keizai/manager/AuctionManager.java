package lobby.one_day_keizai.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public class AuctionManager {

    private final JavaPlugin plugin;
    private final Economy economy;
    private final int auctionIntervalMinutes;
    private final int auctionDurationSeconds;

    private ItemStack currentItem;
    private String currentItemName;
    private final Map<UUID, Double> bids = new HashMap<>();
    private final Map<UUID, Double> balanceSnapshots = new HashMap<>();
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
        loadItemPoolFromConfig();
    }

    /**
     * config.yml の auction-items セクションからアイテムプールを読み込む。
     * プールが空の場合はデフォルトアイテムにフォールバックする。
     */
    public int reloadItemPool() {
        itemPool.clear();
        plugin.reloadConfig();
        loadItemPoolFromConfig();
        return itemPool.size();
    }

    private void loadItemPoolFromConfig() {
        List<?> configItems = plugin.getConfig().getList("auction-items");

        if (configItems == null || configItems.isEmpty()) {
            plugin.getLogger().warning("auction-items が config.yml に見つかりません。デフォルトアイテムを使用します。");
            loadDefaultItemPool();
            return;
        }

        int loaded = 0;
        for (Object obj : configItems) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            String materialName = getString(map, "material");
            int amount = getInt(map, "amount", 1);
            String name = getString(map, "name");

            if (materialName == null || name == null) {
                plugin.getLogger().warning("auction-items: material または name が未設定のエントリをスキップします。");
                continue;
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("auction-items: 不明なマテリアル '" + materialName + "' をスキップします。");
                continue;
            }

            ItemStack item = new ItemStack(material, amount);

            // エンチャント本の場合はエンチャントを付与
            if (material == Material.ENCHANTED_BOOK && map.containsKey("enchantments")) {
                Object enchObj = map.get("enchantments");
                if (enchObj instanceof Map<?, ?> enchMap) {
                    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                    if (meta != null) {
                        for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                            String enchName = entry.getKey().toString();
                            int level = entry.getValue() instanceof Number n ? n.intValue() : 1;
                            Enchantment enchantment = resolveEnchantment(enchName);
                            if (enchantment != null) {
                                meta.addStoredEnchant(enchantment, level, true);
                            } else {
                                plugin.getLogger().warning("auction-items: 不明なエンチャント '" + enchName + "' をスキップします。");
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }
            }

            itemPool.add(new AuctionItem(item, name));
            loaded++;
        }

        if (loaded == 0) {
            plugin.getLogger().warning("auction-items: 有効なアイテムがありません。デフォルトアイテムを使用します。");
            loadDefaultItemPool();
        } else {
            plugin.getLogger().info("auction-items: " + loaded + " 件のアイテムをロードしました。");
        }
    }

    /** エンチャント名（大文字・小文字不問）から Enchantment を解決する */
    @SuppressWarnings("deprecation")
    private Enchantment resolveEnchantment(String name) {
        // まず NamespacedKey で試みる
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT));
        Enchantment ench = Enchantment.getByKey(key);
        if (ench != null) return ench;
        // レガシー名（例: LOOT_BONUS_BLOCKS）でも試みる
        return Enchantment.getByName(name.toUpperCase(java.util.Locale.ROOT));
    }

    private void loadDefaultItemPool() {
        itemPool.add(new AuctionItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 64), "エンチャント瓶 x64"));
        itemPool.add(new AuctionItem(new ItemStack(Material.SADDLE, 1), "サドル"));
        itemPool.add(new AuctionItem(new ItemStack(Material.DIAMOND_BLOCK, 2), "ダイヤブロック x2"));

        ItemStack fortuneBook = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) fortuneBook.getItemMeta();
        if (meta != null) {
            meta.addStoredEnchant(Enchantment.LOOT_BONUS_BLOCKS, 2, true);
            fortuneBook.setItemMeta(meta);
        }
        itemPool.add(new AuctionItem(fortuneBook, "幸運IIのエンチャント本"));

        itemPool.add(new AuctionItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 128), "エンチャント瓶 x128"));
        itemPool.add(new AuctionItem(new ItemStack(Material.BOOKSHELF, 5), "本棚 x5"));
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private int getInt(Map<?, ?> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : def;
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
        balanceSnapshots.clear();
        auctionActive = true;

        // オークション開始時の全オンラインプレイヤーの所持金を記録
        for (Player p : Bukkit.getOnlinePlayers()) {
            balanceSnapshots.put(p.getUniqueId(), economy.getBalance(p));
        }

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

        // 開始時の所持金を上限とする（途中参加はその時点の所持金）
        double maxBid = balanceSnapshots.computeIfAbsent(player.getUniqueId(),
                id -> economy.getBalance(player));
        if (amount > maxBid) {
            player.sendMessage(ChatColor.RED + "入札上限（オークション開始時の所持金: " +
                    String.format("%.0f", maxBid) + "円）を超えています。");
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

        // 入札額の高い順にソート
        List<Map.Entry<UUID, Double>> sortedBids = new ArrayList<>(bids.entrySet());
        sortedBids.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());

        // 入札額が開始時の所持金以内の入札者を上位から探す
        UUID winnerId = null;
        double winningBid = 0;
        for (Map.Entry<UUID, Double> entry : sortedBids) {
            double snapshot = balanceSnapshots.getOrDefault(entry.getKey(), 0.0);
            if (snapshot >= entry.getValue()) {
                winnerId = entry.getKey();
                winningBid = entry.getValue();
                break;
            }
        }

        if (winnerId == null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "  オークション終了 — 残高不足により落札者なし");
            Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
            currentItem = null;
            currentItemName = null;
            return;
        }

        Player winner = Bukkit.getPlayer(winnerId);
        String winnerName = winner != null ? winner.getName() :
                Bukkit.getOfflinePlayer(winnerId).getName();

        // お金を引き落とし（失敗時はアイテム付与しない）
        net.milkbowl.vault.economy.EconomyResponse resp =
                economy.withdrawPlayer(Bukkit.getOfflinePlayer(winnerId), winningBid);
        if (!resp.transactionSuccess()) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "  オークション終了 — 決済失敗（残高不足）により落札者なし");
            currentItem = null;
            currentItemName = null;
            return;
        }

        // アイテム付与
        if (winner != null && winner.isOnline()) {
            giveItem(winner, currentItem);
            winner.sendMessage(ChatColor.GREEN + "オークションで " + currentItemName +
                    " を " + String.format("%.0f", winningBid) + "円で落札しました！");
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "=============================");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  オークション終了！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  落札者: " + ChatColor.WHITE + winnerName);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  アイテム: " + ChatColor.WHITE + currentItemName);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  落札額: " + ChatColor.WHITE +
                String.format("%.0f", winningBid) + "円");
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

    /**
     * アイテムをプールに追加し、config.yml に保存する。
     * @param item        追加するアイテム
     * @param displayName 表示名（nullの場合はマテリアル名から自動生成）
     * @return 追加後のプール内インデックス（1始まり）
     */
    public int addItem(ItemStack item, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            displayName = item.getType().name().toLowerCase().replace('_', ' ');
        }
        itemPool.add(new AuctionItem(item.clone(), displayName));
        saveItemPoolToConfig();
        return itemPool.size();
    }

    /**
     * インデックス（1始まり）でアイテムをプールから削除し、config.yml に保存する。
     * @return 削除したアイテムの表示名、範囲外の場合は null
     */
    public String removeItem(int index) {
        if (index < 1 || index > itemPool.size()) return null;
        AuctionItem removed = itemPool.remove(index - 1);
        saveItemPoolToConfig();
        return removed.name;
    }

    /**
     * 現在のアイテムプールを config.yml の auction-items セクションに書き出す。
     */
    public void saveItemPoolToConfig() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AuctionItem auctionItem : itemPool) {
            Map<String, Object> entry = new LinkedHashMap<>();
            Material mat = auctionItem.item.getType();
            if (mat == null) continue; // 不正なアイテムはスキップ
            entry.put("material", mat.name());
            entry.put("amount", auctionItem.item.getAmount());
            entry.put("name", auctionItem.name);

            // エンチャント本の場合はエンチャント情報も保存
            if (auctionItem.item.getType() == Material.ENCHANTED_BOOK
                    && auctionItem.item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                if (!meta.getStoredEnchants().isEmpty()) {
                    Map<String, Integer> enchants = new LinkedHashMap<>();
                    for (Map.Entry<Enchantment, Integer> e : meta.getStoredEnchants().entrySet()) {
                        enchants.put(e.getKey().getKey().getKey(), e.getValue());
                    }
                    entry.put("enchantments", enchants);
                }
            }
            list.add(entry);
        }
        plugin.getConfig().set("auction-items", list);
        plugin.saveConfig();
        plugin.getLogger().info("auction-items: " + itemPool.size() + " 件を config.yml に保存しました。");
    }

    /** アイテムプールの表示名リストを返す */
    public List<String> getItemPoolNames() {
        List<String> names = new ArrayList<>();
        for (AuctionItem item : itemPool) {
            names.add(item.name);
        }
        return names;
    }

    // テスト用: アイテムプールにアクセス
    List<AuctionItem> getItemPool() {
        return itemPool;
    }
}
