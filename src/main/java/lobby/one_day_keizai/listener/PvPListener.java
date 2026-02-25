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
    private final NametagManager nametagManager;
    private final WorldManager worldManager;
    private final LogoutManager logoutManager;
    private final PlayerDataManager playerDataManager;

    public PvPListener(Economy economy, WantedManager wantedManager, CombatManager combatManager,
                       NametagManager nametagManager, WorldManager worldManager,
                       LogoutManager logoutManager, PlayerDataManager playerDataManager) {
        this.economy = economy;
        this.wantedManager = wantedManager;
        this.combatManager = combatManager;
        this.nametagManager = nametagManager;
        this.worldManager = worldManager;
        this.logoutManager = logoutManager;
        this.playerDataManager = playerDataManager;
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
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        } else {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        double victimDeposit = playerDataManager.getOverworldDeposit(victimId);

        if (inOverworld) {
            worldManager.handleOverworldDeath(victim);
        }

        if (logoutManager != null && logoutManager.isCombatLogoutDeath(victimId)) {
            wantedManager.clearWanted(victimId);
            combatManager.clearAllData(victimId);
            return;
        }

        Player killer = victim.getKiller();

        if (killer == null) {
            wantedManager.clearWanted(victimId);
            combatManager.clearAllData(victimId);
            return;
        }

        UUID killerId = killer.getUniqueId();

        if (wantedManager.isWanted(victimId)) {
            wantedManager.handleWantedDeath(victim, killer);
        }

        if (victimDeposit > 0) {
            economy.depositPlayer(killer, victimDeposit);
            killer.sendMessage(ChatColor.GOLD + victim.getName() + " のデポジット "
                    + String.format("%.0f", victimDeposit) + "G を奪いました。");
            victim.sendMessage(ChatColor.RED + killer.getName() + " に入場料 "
                    + String.format("%.0f", victimDeposit) + "G を奪われました。");
        }

        if (inOverworld) {
            double bounty = playerDataManager.getOverworldDeposit(killerId);
            double currentBounty = wantedManager.isWanted(killerId)
                    ? wantedManager.getBounty(killerId) : bounty;
            wantedManager.makeWanted(killer, currentBounty);
        }

        combatManager.clearCombatData(victimId, killerId);
    }
}
