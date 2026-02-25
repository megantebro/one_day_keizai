package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.data.PlayerDataManager;
import lobby.one_day_keizai.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public class PvPListener implements Listener {

    private final Economy economy;
    private final WantedManager wantedManager;
    private final CombatManager combatManager;
    private final ProtectionManager protectionManager;
    private final NametagManager nametagManager;
    private final WorldManager worldManager;
    private final LogoutManager logoutManager;
    private final PlayerDataManager playerDataManager;
    private final double moneyStealRatio;

    public PvPListener(Economy economy, WantedManager wantedManager, CombatManager combatManager,
                       ProtectionManager protectionManager,
                       NametagManager nametagManager, WorldManager worldManager,
                       LogoutManager logoutManager, PlayerDataManager playerDataManager,
                       double moneyStealRatio) {
        this.economy = economy;
        this.wantedManager = wantedManager;
        this.combatManager = combatManager;
        this.protectionManager = protectionManager;
        this.nametagManager = nametagManager;
        this.worldManager = worldManager;
        this.logoutManager = logoutManager;
        this.playerDataManager = playerDataManager;
        this.moneyStealRatio = moneyStealRatio;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            attacker = p;
        } else {
            return;
        }

        UUID victimId = victim.getUniqueId();
        UUID attackerId = attacker.getUniqueId();

        // 安全ワールドではPVP無効
        if (worldManager.isSafeWorld(victim.getWorld())) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "安全ワールドではPVPできません。");
            return;
        }

        // リスポーン保護チェック
        if (protectionManager.isProtected(victimId)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.GREEN + victim.getName() + " はリスポーン保護中です。");
            return;
        }
        if (protectionManager.isProtected(attackerId)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.GREEN + "リスポーン保護中は攻撃できません。");
            return;
        }

        // 戦闘追跡
        combatManager.recordAttack(attackerId, victimId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        boolean inOverworld = worldManager.isInOverworld(victim);

        // --- アイテムドロップ判定 ---
        if (inOverworld) {
            // オーバーワールド: アイテム全ロスト
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        } else {
            // 安全ワールド: PvP無効のため基本的に来ないが、環境死亡ではアイテム保持
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        // オーバーワールド死亡: デポジット没収
        if (inOverworld) {
            worldManager.handleOverworldDeath(victim);
        }

        // 戦闘ログアウト死亡: LogoutManager で金銭処理済みのためスキップ
        if (logoutManager != null && logoutManager.isCombatLogoutDeath(victimId)) {
            // 指名手配も解除
            wantedManager.clearWanted(victimId);
            combatManager.clearAllData(victimId);
            return;
        }

        Player killer = victim.getKiller();

        if (killer == null) {
            // --- 非PvP死亡: 所持金の一部没収 ---
            double balance = economy.getBalance(victim);
            double penalty = balance * moneyStealRatio;
            if (penalty > 0) {
                economy.withdrawPlayer(victim, penalty);
                victim.sendMessage(ChatColor.RED + "所持金の"
                        + String.format("%.0f%%", moneyStealRatio * 100) + "（"
                        + String.format("%.0f", penalty) + "）を失いました。");
            }
            // 指名手配解除 (非PvP死は時効ではなく単純解除)
            wantedManager.clearWanted(victimId);
            combatManager.clearAllData(victimId);
            return;
        }

        UUID killerId = killer.getUniqueId();

        // --- 指名手配犯の死亡: 懸賞金アイテムをキラーに渡す ---
        if (wantedManager.isWanted(victimId)) {
            wantedManager.handleWantedDeath(victim, killer);
        }

        // --- 金銭処理 ---
        double victimBalance = economy.getBalance(victim);
        double stolenAmount = victimBalance * moneyStealRatio;
        if (stolenAmount > 0) {
            economy.withdrawPlayer(victim, stolenAmount);
            economy.depositPlayer(killer, stolenAmount);
            killer.sendMessage(ChatColor.GOLD + victim.getName() + " から "
                    + String.format("%.0f", stolenAmount) + " を奪いました。");
            victim.sendMessage(ChatColor.RED + killer.getName() + " に "
                    + String.format("%.0f", stolenAmount) + " を奪われました。");
        }

        // --- キラーを指名手配にする (オーバーワールドでのキル) ---
        if (inOverworld) {
            double bounty = playerDataManager.getOverworldDeposit(killerId);
            // 既に指名手配中ならタイマーリセット（懸賞金は変わらず）
            double currentBounty = wantedManager.isWanted(killerId)
                    ? wantedManager.getBounty(killerId) : bounty;
            wantedManager.makeWanted(killer, currentBounty);
        }

        // 戦闘データクリア
        combatManager.clearCombatData(victimId, killerId);
    }

}
