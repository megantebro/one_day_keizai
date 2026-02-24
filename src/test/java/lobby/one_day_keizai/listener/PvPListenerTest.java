package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PvPListenerTest {

    @Mock private Economy economy;
    @Mock private CriminalManager criminalManager;
    @Mock private CombatManager combatManager;
    @Mock private ProtectionManager protectionManager;
    @Mock private DebtManager debtManager;
    @Mock private NametagManager nametagManager;
    @Mock private WorldManager worldManager;
    @Mock private LogoutManager logoutManager;
    @Mock private World overworldMock;

    @Mock private Player victim;
    @Mock private Player killer;

    private PvPListener pvpListener;

    private final UUID victimId = UUID.randomUUID();
    private final UUID killerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pvpListener = new PvPListener(economy, criminalManager, combatManager,
                protectionManager, debtManager, nametagManager, worldManager, logoutManager, 0.33);

        lenient().when(victim.getUniqueId()).thenReturn(victimId);
        lenient().when(killer.getUniqueId()).thenReturn(killerId);
        lenient().when(victim.getName()).thenReturn("Victim");
        lenient().when(killer.getName()).thenReturn("Killer");
        lenient().when(victim.getWorld()).thenReturn(overworldMock);
        lenient().when(worldManager.isSafeWorld(overworldMock)).thenReturn(false);
        lenient().when(worldManager.isInOverworld(victim)).thenReturn(false);
    }

    // =========================================
    // EntityDamageByEntityEvent テスト
    // =========================================

    @Test
    void onDamage_victimProtected_cancelsDamage() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(killer);
        when(protectionManager.isProtected(victimId)).thenReturn(true);

        pvpListener.onEntityDamageByEntity(event);

        verify(event).setCancelled(true);
        verify(combatManager, never()).recordAttack(any(), any());
    }

    @Test
    void onDamage_attackerProtected_cancelsDamage() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(killer);
        when(protectionManager.isProtected(victimId)).thenReturn(false);
        when(protectionManager.isProtected(killerId)).thenReturn(true);

        pvpListener.onEntityDamageByEntity(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onDamage_noProtection_recordsAttack() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(killer);
        when(protectionManager.isProtected(victimId)).thenReturn(false);
        when(protectionManager.isProtected(killerId)).thenReturn(false);

        pvpListener.onEntityDamageByEntity(event);

        verify(event, never()).setCancelled(true);
        verify(combatManager).recordAttack(killerId, victimId);
    }

    @Test
    void onDamage_safeWorld_cancelsPvP() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(killer);
        when(worldManager.isSafeWorld(overworldMock)).thenReturn(true);

        pvpListener.onEntityDamageByEntity(event);

        verify(event).setCancelled(true);
        verify(combatManager, never()).recordAttack(any(), any());
    }

    @Test
    void onDamage_nonPlayerEntity_ignored() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Entity nonPlayer = mock(Entity.class);
        when(event.getEntity()).thenReturn(nonPlayer);

        pvpListener.onEntityDamageByEntity(event);

        verify(combatManager, never()).recordAttack(any(), any());
    }

    // =========================================
    // PlayerDeathEvent テスト
    // =========================================

    @Test
    void onDeath_noKiller_nonCriminal_losesMoneyOnly() {
        PlayerDeathEvent event = createDeathEvent(null);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(economy.getBalance(victim)).thenReturn(3000.0);

        pvpListener.onPlayerDeath(event);

        // 所持金の33%が没収される
        verify(economy).withdrawPlayer(victim, 990.0);
        // アイテムは保持
        assertTrue(event.getKeepInventory());
        assertTrue(event.getKeepLevel());
        verifyNoInteractions(combatManager);
    }

    @Test
    void onDeath_noKiller_criminal_losesItems() {
        PlayerDeathEvent event = createDeathEvent(null);
        when(criminalManager.isCriminal(victimId)).thenReturn(true);

        pvpListener.onPlayerDeath(event);

        // 罪人はアイテム全ドロップ、金銭没収なし
        assertFalse(event.getKeepInventory());
        assertFalse(event.getKeepLevel());
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    @Test
    void onDeath_normalPvP_stealsOneThirdMoney() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(3000.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(false);
        when(criminalManager.getInnocentKillCount(killerId)).thenReturn(1);

        pvpListener.onPlayerDeath(event);

        verify(economy).withdrawPlayer(victim, 990.0);
        verify(economy).depositPlayer(killer, 990.0);
    }

    @Test
    void onDeath_victimIsCriminal_dropsItems() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(true);

        pvpListener.onPlayerDeath(event);

        assertFalse(event.getKeepInventory());
        assertFalse(event.getKeepLevel());
    }

    @Test
    void onDeath_victimNotCriminal_keepsItems() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(false);
        when(criminalManager.getInnocentKillCount(killerId)).thenReturn(1);

        pvpListener.onPlayerDeath(event);

        assertTrue(event.getKeepInventory());
        assertTrue(event.getKeepLevel());
    }

    @Test
    void onDeath_innocentKill_incrementsCount() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(false);
        when(criminalManager.getInnocentKillCount(killerId)).thenReturn(1);

        pvpListener.onPlayerDeath(event);

        verify(criminalManager).incrementInnocentKill(killerId);
    }

    @Test
    void onDeath_selfDefense_doesNotIncrementCount() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(false);

        pvpListener.onPlayerDeath(event);

        verify(criminalManager, never()).incrementInnocentKill(any());
    }

    @Test
    void onDeath_becomeCriminal_setsRedNametag() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(true);

        pvpListener.onPlayerDeath(event);

        verify(nametagManager).setCriminal(killer);
    }

    @Test
    void onDeath_inOverworld_allItemsDropped() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(worldManager.isInOverworld(victim)).thenReturn(true);
        when(economy.getBalance(victim)).thenReturn(3000.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(false);
        when(criminalManager.getInnocentKillCount(killerId)).thenReturn(1);

        pvpListener.onPlayerDeath(event);

        // オーバーワールドでは全員アイテム全ロスト
        assertFalse(event.getKeepInventory());
        assertFalse(event.getKeepLevel());
        // デポジット没収
        verify(worldManager).handleOverworldDeath(victim);
    }

    @Test
    void onDeath_clearsCombatData() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(false);

        pvpListener.onPlayerDeath(event);

        verify(combatManager).clearCombatData(victimId, killerId);
    }

    @Test
    void onDeath_combatLogoutDeath_skipsMoneyProcessing() {
        // 戦闘ログアウト死亡: LogoutManager で money 処理済みのため PvPListener は何もしない
        PlayerDeathEvent event = createDeathEvent(killer);
        when(logoutManager.isCombatLogoutDeath(victimId)).thenReturn(true);

        pvpListener.onPlayerDeath(event);

        // money 処理が一切走っていないこと
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());
        // combatData はクリアされること
        verify(combatManager).clearAllData(victimId);
    }

    // =========================================
    // ヘルパー
    // =========================================

    private PlayerDeathEvent createDeathEvent(Player killerPlayer) {
        List<ItemStack> drops = new ArrayList<>();
        PlayerDeathEvent event = new PlayerDeathEvent(victim, drops, 0, "death message");
        lenient().when(victim.getKiller()).thenReturn(killerPlayer);
        return event;
    }
}
