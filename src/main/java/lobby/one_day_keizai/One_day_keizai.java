package lobby.one_day_keizai;

import lobby.one_day_keizai.command.AuctionCommand;
import lobby.one_day_keizai.command.BalanceCommand;
import lobby.one_day_keizai.command.DebtCommand;
import lobby.one_day_keizai.command.JobCommand;
import lobby.one_day_keizai.command.OverworldCommand;
import lobby.one_day_keizai.data.PlayerDataManager;
import lobby.one_day_keizai.item.BountyItem;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.listener.*;
import lobby.one_day_keizai.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
        double moneyStealRatio = getConfig().getDouble("money-steal-ratio", 0.33);
        int innocentKillLimit = getConfig().getInt("innocent-kill-limit", 3); // DebtManager用に残存
        int respawnProtectionSeconds = getConfig().getInt("respawn-protection-seconds", 600);
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

        // 職業マネージャー
        JobManager jobManager = new JobManager(playerDataManager);

        // マネージャー初期化
        CriminalManager criminalManager = new CriminalManager(playerDataManager, innocentKillLimit);
        NametagManager nametagManager = new NametagManager();
        CombatManager combatManager = new CombatManager(combatLogoutSeconds);
        ProtectionManager protectionManager = new ProtectionManager(
                this, nametagManager, criminalManager, respawnProtectionSeconds);
        DebtManager debtManager = new DebtManager(this, playerDataManager, criminalManager, nametagManager);

        WorldManager worldManager = new WorldManager(
                this, economy, playerDataManager,
                safeWorldName, overworldName, overworldEntryFee, overworldRefundRatio);

        WantedManager wantedManager = new WantedManager(this, nametagManager, wantedDurationSeconds);

        LogoutManager logoutManager = new LogoutManager(
                playerDataManager, combatManager,
                economy, worldManager, logoutGraceMinutes, moneyStealRatio);

        AuctionManager auctionManager = new AuctionManager(
                this, economy, auctionIntervalMinutes, auctionDurationSeconds);
        BalanceScoreboardManager balanceScoreboardManager = new BalanceScoreboardManager(this, economy);

        // リスナー登録
        Bukkit.getPluginManager().registerEvents(
                new PvPListener(economy, wantedManager, combatManager,
                        protectionManager, debtManager, nametagManager, worldManager,
                        logoutManager, playerDataManager, moneyStealRatio), this);
        Bukkit.getPluginManager().registerEvents(
                new PlayerListener(logoutManager, nametagManager, worldManager, jobManager, this), this);
        Bukkit.getPluginManager().registerEvents(new JobSelectionListener(jobManager), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);
        Bukkit.getPluginManager().registerEvents(new WantedListener(worldManager, economy), this);
        Bukkit.getPluginManager().registerEvents(new JobCraftListener(jobManager), this);
        Bukkit.getPluginManager().registerEvents(
                new JobFarmListener(jobManager, safeWorldName), this);

        // コマンド登録
        getCommand("debt").setExecutor(new DebtCommand(debtManager, economy));
        getCommand("debt").setTabCompleter((DebtCommand) getCommand("debt").getExecutor());
        getCommand("bal").setExecutor(new BalanceCommand(economy));
        AuctionCommand auctionCommand = new AuctionCommand(auctionManager);
        getCommand("auction").setExecutor(auctionCommand);
        getCommand("auction").setTabCompleter(auctionCommand);
        OverworldCommand overworldCommand = new OverworldCommand(worldManager, wantedManager);
        getCommand("ow").setExecutor(overworldCommand);
        getCommand("ow").setTabCompleter(overworldCommand);
        JobCommand jobCommand = new JobCommand(jobManager);
        getCommand("job").setExecutor(jobCommand);
        getCommand("job").setTabCompleter(jobCommand);

        // 債権期限チェッカー開始
        debtManager.startDeadlineChecker();

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
