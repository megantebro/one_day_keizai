package lobby.one_day_keizai;

import lobby.one_day_keizai.command.BalanceCommand;
import lobby.one_day_keizai.command.DebtCommand;
import lobby.one_day_keizai.data.PlayerDataManager;
import lobby.one_day_keizai.listener.PlayerListener;
import lobby.one_day_keizai.listener.PvPListener;
import lobby.one_day_keizai.listener.WorldListener;
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

        // Vault Economy セットアップ
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault Economy が見つかりません。プラグインを無効化します。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 設定値読み込み
        double moneyStealRatio = getConfig().getDouble("money-steal-ratio", 0.33);
        int innocentKillLimit = getConfig().getInt("innocent-kill-limit", 3);
        int respawnProtectionSeconds = getConfig().getInt("respawn-protection-seconds", 600);
        int combatLogoutSeconds = getConfig().getInt("combat-logout-seconds", 30);
        int logoutGraceMinutes = getConfig().getInt("logout-grace-minutes", 15);
        int bedLogoutRadius = getConfig().getInt("bed-logout-radius", 10);
        int bedStaySeconds = getConfig().getInt("bed-stay-seconds", 15);

        // データ管理
        playerDataManager = new PlayerDataManager(this);

        // マネージャー初期化
        CriminalManager criminalManager = new CriminalManager(playerDataManager, innocentKillLimit);
        NametagManager nametagManager = new NametagManager();
        CombatManager combatManager = new CombatManager(combatLogoutSeconds);
        ProtectionManager protectionManager = new ProtectionManager(
                this, nametagManager, criminalManager, respawnProtectionSeconds);
        DebtManager debtManager = new DebtManager(this, playerDataManager, criminalManager, nametagManager);
        LogoutManager logoutManager = new LogoutManager(
                this, playerDataManager, combatManager, criminalManager, nametagManager,
                economy, bedLogoutRadius, logoutGraceMinutes, moneyStealRatio, bedStaySeconds);

        // リスナー登録
        Bukkit.getPluginManager().registerEvents(
                new PvPListener(economy, criminalManager, combatManager,
                        protectionManager, debtManager, nametagManager, moneyStealRatio), this);
        Bukkit.getPluginManager().registerEvents(
                new PlayerListener(criminalManager, protectionManager, logoutManager, nametagManager), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);

        // コマンド登録
        getCommand("debt").setExecutor(new DebtCommand(debtManager, economy));
        getCommand("debt").setTabCompleter((DebtCommand) getCommand("debt").getExecutor());
        getCommand("bal").setExecutor(new BalanceCommand(economy));

        // ベッド付近滞在トラッカー開始
        logoutManager.startBedProximityTracker();

        // 債権期限チェッカー開始
        debtManager.startDeadlineChecker();

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
