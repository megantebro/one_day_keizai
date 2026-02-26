package lobby.one_day_keizai;

import lobby.one_day_keizai.command.AuctionCommand;
import lobby.one_day_keizai.command.FarmCommand;
import lobby.one_day_keizai.command.CompassCommand;
import lobby.one_day_keizai.command.ShopCommand;
import lobby.one_day_keizai.manager.CompassManager;
import lobby.one_day_keizai.command.BalanceCommand;
import lobby.one_day_keizai.command.JobCommand;
import lobby.one_day_keizai.command.OverworldCommand;
import lobby.one_day_keizai.command.StockCommand;
import lobby.one_day_keizai.data.PlayerDataManager;
import lobby.one_day_keizai.data.StockDataManager;
import lobby.one_day_keizai.item.BountyItem;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.listener.*;
import lobby.one_day_keizai.manager.*;
import lobby.one_day_keizai.ui.WantedCompassUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class One_day_keizai extends JavaPlugin {

    private Economy economy;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        // config.yml 読み込み
        saveDefaultConfig();

        // BountyItem NamespacedKey 初期化
        BountyItem.init(this);

        // Vault Economy セットアップ
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault Economy が見つかりません。プラグインを無効化します。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 設定値読み込み
        int combatLogoutSeconds = getConfig().getInt("combat-logout-seconds", 30);
        int logoutGraceMinutes = getConfig().getInt("logout-grace-minutes", 15);
        int wantedDurationSeconds = getConfig().getInt("wanted-duration-seconds", 1800); // 30分
        int auctionIntervalMinutes = getConfig().getInt("auction-interval-minutes", 30);
        int auctionDurationSeconds = getConfig().getInt("auction-duration-seconds", 120);
        String safeWorldName = getConfig().getString("safe-world-name", "economy");
        String overworldName = getConfig().getString("overworld-name", "world");
        double overworldEntryFee = getConfig().getDouble("overworld-entry-fee", 1000);
        double overworldRefundRatio = getConfig().getDouble("overworld-refund-ratio", 0.8);

        // データ管理
        playerDataManager = new PlayerDataManager(this);

        // 上級職設定
        double jobPromoteFee = getConfig().getDouble("job-promote-fee", 200000);
        double wealthyMerchantShopFee = getConfig().getDouble("wealthy-merchant-shop-fee", 10000);
        org.bukkit.Location wealthyMerchantShopLocation = null;
        if (getConfig().contains("wealthy-merchant-shop-world")) {
            String shopWorld = getConfig().getString("wealthy-merchant-shop-world");
            double shopX = getConfig().getDouble("wealthy-merchant-shop-x", 0);
            double shopY = getConfig().getDouble("wealthy-merchant-shop-y", 64);
            double shopZ = getConfig().getDouble("wealthy-merchant-shop-z", 0);
            org.bukkit.World w = Bukkit.getWorld(shopWorld);
            if (w != null) {
                wealthyMerchantShopLocation = new org.bukkit.Location(w, shopX, shopY, shopZ);
            } else {
                getLogger().warning("豪商ショップワールド '" + shopWorld + "' が見つかりません。/job shop は使用できません。");
            }
        }

        // 職業マネージャー
        JobManager jobManager = new JobManager(playerDataManager);

        // マネージャー初期化
        NametagManager nametagManager = new NametagManager();
        nametagManager.setJobManager(jobManager);
        CombatManager combatManager = new CombatManager(combatLogoutSeconds);

        WorldManager worldManager = new WorldManager(
                this, economy, playerDataManager,
                safeWorldName, overworldName, overworldEntryFee, overworldRefundRatio);

        WantedManager wantedManager = new WantedManager(this, nametagManager, economy, playerDataManager, wantedDurationSeconds);

        LogoutManager logoutManager = new LogoutManager(
                playerDataManager, combatManager,
                economy, worldManager, logoutGraceMinutes);

        AuctionManager auctionManager = new AuctionManager(
                this, economy, auctionIntervalMinutes, auctionDurationSeconds);
        BalanceScoreboardManager balanceScoreboardManager = new BalanceScoreboardManager(this, economy);

        // 株・投資システム
        double stockSellFee = getConfig().getDouble("stock-sell-fee", 0.20);
        double stockDividendRate = getConfig().getDouble("stock-dividend-rate", 0.01);
        int stockDividendIntervalMinutes = getConfig().getInt("stock-dividend-interval-minutes", 60);
        StockDataManager stockDataManager = new StockDataManager(this);
        StockManager stockManager = new StockManager(this, economy, jobManager,
                stockDataManager, stockSellFee, stockDividendRate, stockDividendIntervalMinutes);

        // コンパスマネージャー（リスナー登録前に初期化）
        CompassManager compassManager = new CompassManager(this, wantedManager, worldManager);
        compassManager.startUpdateTask();

        // リスナー登録
        Bukkit.getPluginManager().registerEvents(
                new PvPListener(economy, wantedManager, combatManager,
                        nametagManager, worldManager,
                        logoutManager, playerDataManager, jobManager), this);
        Bukkit.getPluginManager().registerEvents(
                new PlayerListener(logoutManager, nametagManager, worldManager, jobManager, this), this);
        Bukkit.getPluginManager().registerEvents(new JobSelectionListener(jobManager, nametagManager), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);
        Bukkit.getPluginManager().registerEvents(new WantedListener(worldManager, wantedManager), this);
        Bukkit.getPluginManager().registerEvents(new JobCraftListener(jobManager), this);
        JobFarmListener farmListener = new JobFarmListener(jobManager, safeWorldName, this);
        Bukkit.getPluginManager().registerEvents(farmListener, this);
        Bukkit.getPluginManager().registerEvents(new EnchantTableListener(jobManager), this);
        Bukkit.getPluginManager().registerEvents(new DonkeyChestListener(this, jobManager), this);
        WantedCompassUI wantedCompassUI = new WantedCompassUI(wantedManager, this);
        Bukkit.getPluginManager().registerEvents(wantedCompassUI, this);
        Bukkit.getPluginManager().registerEvents(new CompassListener(compassManager, jobManager, wantedCompassUI), this);

        // 農家専用レシピ: 小麦俵 x1 → エンチャント瓶 x2
        NamespacedKey farmerHayRecipeKey = new NamespacedKey(this, "farmer_hay_to_xpbottle");
        Bukkit.removeRecipe(farmerHayRecipeKey); // plugman reload 時の重複防止
        ShapelessRecipe farmerHayRecipe = new ShapelessRecipe(
                farmerHayRecipeKey, new ItemStack(Material.EXPERIENCE_BOTTLE, 2));
        farmerHayRecipe.addIngredient(Material.HAY_BLOCK);
        Bukkit.addRecipe(farmerHayRecipe);

        // コマンド登録
        getCommand("bal").setExecutor(new BalanceCommand(economy));
        AuctionCommand auctionCommand = new AuctionCommand(auctionManager);
        getCommand("auction").setExecutor(auctionCommand);
        getCommand("auction").setTabCompleter(auctionCommand);
        OverworldCommand overworldCommand = new OverworldCommand(worldManager, wantedManager);
        getCommand("ow").setExecutor(overworldCommand);
        getCommand("ow").setTabCompleter(overworldCommand);
        JobCommand jobCommand = new JobCommand(jobManager, economy, nametagManager,
                jobPromoteFee, wealthyMerchantShopFee, wealthyMerchantShopLocation);
        getCommand("job").setExecutor(jobCommand);
        getCommand("job").setTabCompleter(jobCommand);
        StockCommand stockCommand = new StockCommand(stockManager);
        getCommand("stock").setExecutor(stockCommand);
        getCommand("stock").setTabCompleter(stockCommand);

        ShopCommand shopCommand = new ShopCommand(this, jobManager);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        FarmCommand farmCommand = new FarmCommand(jobManager, farmListener);
        getCommand("farm").setExecutor(farmCommand);
        getCommand("farm").setTabCompleter(farmCommand);

        CompassCommand compassCommand = new CompassCommand(compassManager);
        getCommand("compass").setExecutor(compassCommand);
        getCommand("compass").setTabCompleter(compassCommand);

        // 株配当スケジューラー開始
        stockManager.startDividendScheduler();

        // オークションスケジューラー開始
        auctionManager.startAuctionScheduler();

        // Balance Top スコアボード開始
        balanceScoreboardManager.startUpdater();

        // 定期自動保存（5分ごと）
        Bukkit.getScheduler().runTaskTimer(this, () -> playerDataManager.save(), 20L * 300, 20L * 300);

        getLogger().info("One Day Keizai プラグインが有効化されました。");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.save();
        }
        getLogger().info("One Day Keizai プラグインが無効化されました。");
    }

    // stockDataManager の参照を onDisable で使うため field に昇格
    // ※ stockDataManager は StockManager 内で保持されているため、
    //   追加の save() 呼び出しは不要 (StockManager が各操作後に save() を呼ぶ)

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }
}
