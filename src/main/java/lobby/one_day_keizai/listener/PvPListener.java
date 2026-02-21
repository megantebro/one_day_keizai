package lobby.one_day_keizai.listener;

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
    private final CriminalManager criminalManager;
    private final CombatManager combatManager;
    private final ProtectionManager protectionManager;
    private final DebtManager debtManager;
    private final NametagManager nametagManager;
    private final double moneyStealRatio;

    public PvPListener(Economy economy, CriminalManager criminalManager, CombatManager combatManager,
                       ProtectionManager protectionManager, DebtManager debtManager,
                       NametagManager nametagManager, double moneyStealRatio) {
        this.economy = economy;
        this.criminalManager = criminalManager;
        this.combatManager = combatManager;
        this.protectionManager = protectionManager;
        this.debtManager = debtManager;
        this.nametagManager = nametagManager;
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

        // リスポーン保護チェック（攻撃側または被攻撃側が保護中ならキャンセル）
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

        // --- 罪人判定: アイテムドロップ（全死因共通）---
        if (criminalManager.isCriminal(victimId)) {
            // 罪人はアイテム全ドロップ
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        } else {
            // 非罪人はアイテム保持
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player killer = victim.getKiller();
        if (killer == null) {
            // --- PvP以外の死亡：非罪人は所持金の3割没収 ---
            if (!criminalManager.isCriminal(victimId)) {
                double balance = economy.getBalance(victim);
                double penalty = balance * moneyStealRatio;
                if (penalty > 0) {
                    economy.withdrawPlayer(victim, penalty);
                }
                victim.sendMessage(ChatColor.RED + "所持金の"
                        + String.format("%.0f%%", moneyStealRatio * 100) + "（"
                        + String.format("%.0f", penalty) + "）を失いました。");
            }
            return;
        }

        UUID killerId = killer.getUniqueId();

        // --- 債務者の死亡時特殊処理 ---
        if (debtManager.isDebtor(victimId)) {
            handleDebtorDeath(victim, killer, victimId, killerId, event);
        } else {
            // --- 通常のPvP金銭処理 ---
            double victimBalance = economy.getBalance(victim);
            double stolenAmount = victimBalance * moneyStealRatio;
            if (stolenAmount > 0) {
                economy.withdrawPlayer(victim, stolenAmount);
                economy.depositPlayer(killer, stolenAmount);
                killer.sendMessage(ChatColor.GOLD + victim.getName() + " から " + String.format("%.0f", stolenAmount) + " を奪いました。");
                victim.sendMessage(ChatColor.RED + killer.getName() + " に " + String.format("%.0f", stolenAmount) + " を奪われました。");
            }
        }

        // --- 正当防衛チェック & 罪人カウント（罪人を殺した場合はカウントしない）---
        if (!criminalManager.isCriminal(victimId) && combatManager.isInnocentKill(killerId, victimId)) {
            boolean becameCriminal = criminalManager.incrementInnocentKill(killerId);
            if (becameCriminal) {
                killer.sendMessage(ChatColor.DARK_RED + "あなたは罪人になりました！");
                nametagManager.setCriminal(killer);
            } else {
                int count = criminalManager.getInnocentKillCount(killerId);
                int limit = 3; // config value
                killer.sendMessage(ChatColor.YELLOW + "無実キル: " + count + "/" + limit);
            }
        }

        // 戦闘データクリア
        combatManager.clearCombatData(victimId, killerId);
    }

    private void handleDebtorDeath(Player victim, Player killer, UUID victimId, UUID killerId, PlayerDeathEvent event) {
        double victimBalance = economy.getBalance(victim);

        // 債権者を取得
        UUID creditorId = debtManager.getCreditor(victimId);

        if (creditorId != null) {
            // 所持金の半分を債権者、半分をキラーに
            double halfBalance = victimBalance / 2.0;

            if (halfBalance > 0) {
                economy.withdrawPlayer(victim, victimBalance);

                // キラーが債権者と同じ場合は全額キラーへ
                if (killerId.equals(creditorId)) {
                    economy.depositPlayer(killer, victimBalance);
                    killer.sendMessage(ChatColor.GOLD + "債務者 " + victim.getName() + " から " + String.format("%.0f", victimBalance) + " を回収しました。");
                } else {
                    economy.depositPlayer(killer, halfBalance);
                    // 債権者がオンラインの場合
                    Player creditor = victim.getServer().getPlayer(creditorId);
                    if (creditor != null) {
                        economy.depositPlayer(creditor, halfBalance);
                        creditor.sendMessage(ChatColor.GOLD + "債務者 " + victim.getName() + " の死亡により " + String.format("%.0f", halfBalance) + " を回収しました。");
                    } else {
                        // オフラインの場合はOfflinePlayerで処理
                        economy.depositPlayer(victim.getServer().getOfflinePlayer(creditorId), halfBalance);
                    }
                    killer.sendMessage(ChatColor.GOLD + victim.getName() + " から " + String.format("%.0f", halfBalance) + " を奪いました。");
                }
            }

            // 債務帳消し
            debtManager.clearDebtsForDebtor(victimId);
            victim.sendMessage(ChatColor.YELLOW + "死亡により債務が帳消しになりました。");
        }
    }
}
