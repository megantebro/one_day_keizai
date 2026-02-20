package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LogoutManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager dataManager;
    private final CombatManager combatManager;
    private final CriminalManager criminalManager;
    private final NametagManager nametagManager;
    private final Economy economy;
    private final int bedLogoutRadius;
    private final int logoutGraceMinutes;
    private final double moneyStealRatio;
    private final int bedStaySeconds;

    private final Map<UUID, Long> bedProximityTimestamps = new ConcurrentHashMap<>();
    private final Set<UUID> bedStayNotified = ConcurrentHashMap.newKeySet();

    public LogoutManager(JavaPlugin plugin, PlayerDataManager dataManager, CombatManager combatManager,
                         CriminalManager criminalManager, NametagManager nametagManager,
                         Economy economy, int bedLogoutRadius, int logoutGraceMinutes,
                         double moneyStealRatio, int bedStaySeconds) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.combatManager = combatManager;
        this.criminalManager = criminalManager;
        this.nametagManager = nametagManager;
        this.economy = economy;
        this.bedLogoutRadius = bedLogoutRadius;
        this.logoutGraceMinutes = logoutGraceMinutes;
        this.moneyStealRatio = moneyStealRatio;
        this.bedStaySeconds = bedStaySeconds;
    }

    /**
     * ベッド付近の滞在時間チェックを開始する。毎秒実行。
     */
    public void startBedProximityTracker() {
        Bukkit.getLogger().info("start");
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                Location bedLoc = p.getBedSpawnLocation();

                if (bedLoc == null) {
                    bedProximityTimestamps.remove(uuid);
                    bedStayNotified.remove(uuid);
                    continue;
                }

                Location playerLoc = p.getLocation();
                boolean nearBed = playerLoc.getWorld().equals(bedLoc.getWorld())
                        && playerLoc.distance(bedLoc) <= bedLogoutRadius;

                if (nearBed) {
                    bedProximityTimestamps.putIfAbsent(uuid, System.currentTimeMillis());
                    Long entryTime = bedProximityTimestamps.get(uuid);
                    if (entryTime != null) {
                        long elapsedMs = System.currentTimeMillis() - entryTime;
                        int remaining = bedStaySeconds - (int) (elapsedMs / 1000L);

                        if (remaining > 0) {
                            // カウントダウンをアクションバーに表示
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    new TextComponent(ChatColor.YELLOW + "安全ログアウトまで: "
                                            + remaining + "秒"));
                        } else if (bedStayNotified.add(uuid)) {
                            // カウントダウン完了
                            p.sendMessage(ChatColor.GREEN + "ベッド付近に" + bedStaySeconds
                                    + "秒滞在しました。安全にログアウトできます。");
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    new TextComponent(ChatColor.GREEN + "安全にログアウトできます"));
                        }
                    }
                } else {
                    bedProximityTimestamps.remove(uuid);
                    bedStayNotified.remove(uuid);
                }
            }
        }, 20L, 20L);
    }

    /**
     * ベッド付近に十分な時間滞在しているかチェック。
     */
    public boolean hasStayedNearBed(UUID uuid) {
        Long entryTime = bedProximityTimestamps.get(uuid);
        if (entryTime == null) return false;
        return System.currentTimeMillis() - entryTime >= bedStaySeconds * 1000L;
    }

    /**
     * プレイヤーログアウト時の処理。
     */
    public void handleLogout(Player player) {
        UUID uuid = player.getUniqueId();

        // 戦闘ログアウトチェック
        if (combatManager.isInCombat(uuid)) {
            handleCombatLogout(player);
            cleanupProximityData(uuid);
            return;
        }

        // ベッド付近チェック
        Location bedLoc = player.getBedSpawnLocation();
        if (bedLoc == null) {
            applyLogoutPenalty(player);
            cleanupProximityData(uuid);
            return;
        }

        Location playerLoc = player.getLocation();
        if (!playerLoc.getWorld().equals(bedLoc.getWorld()) ||
                playerLoc.distance(bedLoc) > bedLogoutRadius) {
            applyLogoutPenalty(player);
            cleanupProximityData(uuid);
            return;
        }

        // ベッド付近に15秒間滞在しているかチェック
        if (!hasStayedNearBed(uuid)) {
            applyLogoutPenalty(player);
            cleanupProximityData(uuid);
            return;
        }

        // 正常ログアウト
        cleanupProximityData(uuid);
    }

    private void cleanupProximityData(UUID uuid) {
        bedProximityTimestamps.remove(uuid);
        bedStayNotified.remove(uuid);
    }

    /**
     * 戦闘ログアウト処理：最後の攻撃者にキル判定。
     */
    private void handleCombatLogout(Player victim) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = combatManager.getLastAttacker(victimId);

        if (attackerId != null) {
            Player attacker = Bukkit.getPlayer(attackerId);

            // 金銭奪取
            double victimBalance = economy.getBalance(victim);
            double stolenAmount = victimBalance * moneyStealRatio;
            if (stolenAmount > 0) {
                economy.withdrawPlayer(victim, stolenAmount);
                if (attacker != null) {
                    economy.depositPlayer(attacker, stolenAmount);
                    attacker.sendMessage(ChatColor.GOLD + victim.getName() + " が戦闘ログアウトしました。" +
                            String.format("%.0f", stolenAmount) + " を獲得しました。");
                } else {
                    economy.depositPlayer(Bukkit.getOfflinePlayer(attackerId), stolenAmount);
                }
            }

            // 戦闘ログアウトは無実キルカウントに含めない（悪用防止）

            // 罪人ならアイテムドロップ
            if (criminalManager.isCriminal(victimId)) {
                Location loc = victim.getLocation();
                for (ItemStack item : victim.getInventory().getContents()) {
                    if (item != null) {
                        loc.getWorld().dropItemNaturally(loc, item);
                    }
                }
                victim.getInventory().clear();
            }
        }

        combatManager.clearAllData(victimId);
        Bukkit.broadcastMessage(ChatColor.RED + victim.getName() + " は戦闘中にログアウトしました！");
    }

    /**
     * ベッド付近外ログアウトペナルティ：データ保存、猶予期間設定。
     */
    private void applyLogoutPenalty(Player player) {
        UUID uuid = player.getUniqueId();
        long deadline = System.currentTimeMillis() + (logoutGraceMinutes * 60 * 1000L);
        dataManager.setLogoutPenaltyData(uuid, player.getLocation(),
                player.getInventory().getContents(), deadline);
        dataManager.save();
        player.sendMessage(ChatColor.RED + "ベッド付近以外でのログアウトです。" +
                logoutGraceMinutes + "分以内にログインしないとアイテムを失います。");
    }

    /**
     * プレイヤーログイン時のペナルティチェック。
     */
    public void handleLogin(Player player) {
        UUID uuid = player.getUniqueId();

        if (!dataManager.hasLogoutPenaltyData(uuid)) return;

        long deadline = dataManager.getLogoutPenaltyDeadline(uuid);

        if (System.currentTimeMillis() > deadline) {
            // 猶予期間切れ → 死亡
            player.setHealth(0);
            player.sendMessage(ChatColor.DARK_RED + "ログアウトペナルティ：猶予期間が過ぎたため死亡しました。");
        } else {
            player.sendMessage(ChatColor.GREEN + "猶予期間内にログインしました。アイテムは保持されます。");
        }

        dataManager.clearLogoutPenaltyData(uuid);
        dataManager.save();
    }

    /**
     * テスト用：ベッド付近滞在タイムスタンプを設定する。
     */
    void setBedProximityTimestamp(UUID uuid, long timestamp) {
        bedProximityTimestamps.put(uuid, timestamp);
    }
}
