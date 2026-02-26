package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.data.PlayerDataManager;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PvPListener implements Listener {

    /** 鍛冶屋が死亡時にロストしないツール素材 */
    private static final Set<Material> TOOL_MATERIALS = EnumSet.of(
        // ピッケル
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
        // シャベル
        Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
        Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
        // 斧
        Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
        // クワ
        Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
        Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );

    /** 死亡〜リスポーン間の一時保管ツール */
    private final Map<UUID, List<ItemStack>> savedTools = new HashMap<>();

    private final Economy economy;
    private final WantedManager wantedManager;
    private final CombatManager combatManager;
    private final NametagManager nametagManager;
    private final WorldManager worldManager;
    private final LogoutManager logoutManager;
    private final PlayerDataManager playerDataManager;
    private final JobManager jobManager;

    public PvPListener(Economy economy, WantedManager wantedManager, CombatManager combatManager,
                       NametagManager nametagManager, WorldManager worldManager,
                       LogoutManager logoutManager, PlayerDataManager playerDataManager,
                       JobManager jobManager) {
        this.economy = economy;
        this.wantedManager = wantedManager;
        this.combatManager = combatManager;
        this.nametagManager = nametagManager;
        this.worldManager = worldManager;
        this.logoutManager = logoutManager;
        this.playerDataManager = playerDataManager;
        this.jobManager = jobManager;
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

            // 鍛冶屋はツール類をロストしない
            if (jobManager.isBlacksmith(victimId)) {
                List<ItemStack> tools = new ArrayList<>();
                Iterator<ItemStack> it = event.getDrops().iterator();
                while (it.hasNext()) {
                    ItemStack item = it.next();
                    if (item != null && TOOL_MATERIALS.contains(item.getType())) {
                        tools.add(item.clone());
                        it.remove();
                    }
                }
                if (!tools.isEmpty()) {
                    savedTools.put(victimId, tools);
                }
            }
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

        // handleWantedDeath 前に「指名手配だったか」を記録しておく
        boolean victimWasWanted = wantedManager.isWanted(victimId);

        if (victimWasWanted) {
            wantedManager.handleWantedDeath(victim, killer);
        }

        if (victimDeposit > 0) {
            economy.depositPlayer(killer, victimDeposit);
            killer.sendMessage(ChatColor.GOLD + victim.getName() + " のデポジット "
                    + String.format("%.0f", victimDeposit) + "G を奪いました。");
            victim.sendMessage(ChatColor.RED + killer.getName() + " に入場料 "
                    + String.format("%.0f", victimDeposit) + "G を奪われました。");
        }

        // 指名手配中のプレイヤーを倒した場合は手配されない（正当防衛）
        if (inOverworld && !victimWasWanted) {
            double bounty = playerDataManager.getOverworldDeposit(killerId);
            double currentBounty = wantedManager.isWanted(killerId)
                    ? wantedManager.getBounty(killerId) : bounty;
            wantedManager.makeWanted(killer, currentBounty);
        }

        combatManager.clearCombatData(victimId, killerId);
    }

    /** リスポーン時、保存していたツールを返却する */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        List<ItemStack> tools = savedTools.remove(playerId);
        if (tools == null || tools.isEmpty()) return;

        Player player = event.getPlayer();
        for (ItemStack tool : tools) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(tool);
            // 満杯の場合はドロップ
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        player.sendMessage(ChatColor.AQUA + "鍛冶屋特権: ツールが返却されました。");
    }
}
