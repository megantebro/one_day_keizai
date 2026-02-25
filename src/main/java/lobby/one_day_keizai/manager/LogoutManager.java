package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ログアウト・ペナルティ管理。
 * 安全ログアウト条件: 安全ワールドにいること（ベッド条件は廃止）。
 */
public class LogoutManager {

    private final PlayerDataManager dataManager;
    private final CombatManager combatManager;
    private final Economy economy;
    private final WorldManager worldManager;
    private final int logoutGraceMinutes;

    // 戦闘ログアウト死亡処理中フラグ: PvPListenerでの二重金銭没収を防ぐ
    private final Set<UUID> combatLogoutDeaths = ConcurrentHashMap.newKeySet();

    public LogoutManager(PlayerDataManager dataManager,
                         CombatManager combatManager,
                         Economy economy, WorldManager worldManager,
                         int logoutGraceMinutes) {
        this.dataManager = dataManager;
        this.combatManager = combatManager;
        this.economy = economy;
        this.worldManager = worldManager;
        this.logoutGraceMinutes = logoutGraceMinutes;
    }

    /**
     * プレイヤーログアウト時の処理。
     * 安全ワールドにいる場合は安全ログアウト、それ以外はペナルティ。
     */
    public void handleLogout(Player player) {
        UUID uuid = player.getUniqueId();

        // 戦闘ログアウトチェック（最優先）
        if (combatManager.isInCombat(uuid)) {
            handleCombatLogout(player);
            return;
        }

        // 安全ワールドにいる場合は安全ログアウト
        if (worldManager.isSafeWorld(player.getWorld())) {
            return;
        }

        // 安全ワールド外ログアウトペナルティ
        applyLogoutPenalty(player);
    }

    /**
     * 戦闘ログアウト死亡処理中かどうかを返す。
     * PvPListener.onPlayerDeath での二重金銭没収防止に使用。
     */
    public boolean isCombatLogoutDeath(UUID uuid) {
        return combatLogoutDeaths.contains(uuid);
    }

    /**
     * 戦闘ログアウト処理：最後の攻撃者にキル判定。
     * 金銭処理は setHealth(0) より先に行い、PlayerDeathEvent での二重没収を防ぐ。
     */
    private void handleCombatLogout(Player victim) {
        UUID victimId = victim.getUniqueId();
        UUID attackerId = combatManager.getLastAttacker(victimId);

        if (attackerId != null) {
            Player attacker = Bukkit.getPlayer(attackerId);

            // デポジット（入場料）を攻撃者に渡す
            double deposit = dataManager.getOverworldDeposit(victimId);
            if (deposit > 0) {
                if (attacker != null) {
                    economy.depositPlayer(attacker, deposit);
                    attacker.sendMessage(ChatColor.GOLD + victim.getName() + " が戦闘ログアウトしました。"
                            + "デポジット " + String.format("%.0f", deposit) + "G を獲得しました。");
                } else {
                    economy.depositPlayer(Bukkit.getOfflinePlayer(attackerId), deposit);
                }
            }
        }

        // setHealth(0) が PlayerDeathEvent を発火させる前にフラグを立てる
        combatLogoutDeaths.add(victimId);
        victim.setHealth(0);
        combatLogoutDeaths.remove(victimId);

        combatManager.clearAllData(victimId);
        Bukkit.broadcastMessage(ChatColor.RED + victim.getName() + " は戦闘中にログアウトしました！");
    }

    /**
     * 安全ワールド外ログアウトペナルティ: 猶予時間を設定する。
     */
    private void applyLogoutPenalty(Player player) {
        UUID uuid = player.getUniqueId();
        long deadline = System.currentTimeMillis() + ((long) logoutGraceMinutes * 60 * 1000L);
        dataManager.setLogoutPenaltyData(uuid, player.getLocation(),
                player.getInventory().getContents(), deadline);
        dataManager.save();
        player.sendMessage(ChatColor.RED + "安全ワールド外でのログアウトです。"
                + logoutGraceMinutes + "分以内にログインしないとアイテムを失います。");
    }

    /**
     * プレイヤーログイン時のペナルティチェック。
     */
    public void handleLogin(Player player) {
        UUID uuid = player.getUniqueId();

        if (!dataManager.hasLogoutPenaltyData(uuid)) return;

        long deadline = dataManager.getLogoutPenaltyDeadline(uuid);

        if (System.currentTimeMillis() > deadline) {
            // 猶予期間超過: アイテム全ロスト（所持金ペナルティなし）
            player.getInventory().clear();
            player.sendMessage(ChatColor.RED + "ログアウトペナルティ：猶予時間を超過したためアイテムを失いました。");
        } else {
            player.sendMessage(ChatColor.GREEN + "猶予期間内にログインしました。アイテムは保持されます。");
        }

        dataManager.clearLogoutPenaltyData(uuid);
        dataManager.save();
    }
}
