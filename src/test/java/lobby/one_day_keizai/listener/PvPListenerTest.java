package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.data.PlayerDataManager;
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
    @Mock private WantedManager wantedManager;
    @Mock private CombatManager combatManager;
    @Mock private ProtectionManager protectionManager;
    @Mock private NametagManager nametagManager;
    @Mock private WorldManager worldManager;
    @Mock private LogoutManager logoutManager;
    @Mock private PlayerDataManager playerDataManager;
    @Mock private World overworldMock;

    @Mock private Player victim;
    @Mock private Player killer;

    private PvPListener pvpListener;

    private final UUID victimId = UUID.randomUUID();
    private final UUID killerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pvpListener = new PvPListener(economy, wantedManager, combatManager,
                protectionManager, nametagManager, worldManager,
                logoutManager, playerDataManager, 0.33);

        lenient().when(victim.getUniqueId()).thenReturn(victimId);
        lenient().when(killer.getUniqueId()).thenReturn(killerId);
        lenient().when(victim.getName()).thenReturn("Victim");
        lenient().when(killer.getName()).thenReturn("Killer");
        lenient().when(victim.getWorld()).thenReturn(overworldMock);
        lenient().when(worldManager.isSafeWorld(overworldMock)).thenReturn(false);
        lenient().when(worldManager.isInOverworld(victim)).thenReturn(false);
        lenient().when(wantedManager.isWanted(any())).thenReturn(false);
        lenient().when(logoutManager.isCombatLogoutDeath(any())).thenReturn(false);
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
    void onDeath_noKiller_losesMoneyOnly() {
        PlayerDeathEvent event = createDeathEvent(null);
        when(economy.getBalance(victim)).thenReturn(3000.0);

        pvpListener.onPlayerDeath(event);

        verify(economy).withdrawPlayer(victim, 990.0);
        assertTrue(event.getKeepInventory());
        assertTrue(event.getKeepLevel());
    }

    @Test
    void onDeath_normalPvP_stealsOneThirdMoney() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(3000.0);

        pvpListener.onPlayerDeath(event);

        verify(economy).withdrawPlayer(victim, 990.0);
        verify(economy).depositPlayer(killer, 990.0);
    }

    @Test
    void onDeath_inOverworld_allItemsDropped() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(worldManager.isInOverworld(victim)).thenReturn(true);
        when(economy.getBalance(victim)).thenReturn(3000.0);
        when(playerDataManager.getOverworldDeposit(killerId)).thenReturn(1000.0);

        pvpListener.onPlayerDeath(event);

        assertFalse(event.getKeepInventory());
        assertFalse(event.getKeepLevel());
        verify(worldManager).handleOverworldDeath(victim);
    }

    @Test
    void onDeath_victimWanted_callsHandleWantedDeath() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(wantedManager.isWanted(victimId)).thenReturn(true);
        when(economy.getBalance(victim)).thenReturn(0.0);

        pvpListener.onPlayerDeath(event);

        verify(wantedManager).handleWantedDeath(victim, killer);
    }

    @Test
    void onDeath_killerInOverworld_becomesWanted() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(worldManager.isInOverworld(victim)).thenReturn(true);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(playerDataManager.getOverworldDeposit(killerId)).thenReturn(500.0);

        pvpListener.onPlayerDeath(event);

        verify(wantedManager).makeWanted(killer, 500.0);
    }

    @Test
    void onDeath_killerAlreadyWanted_keepsBounty() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(worldManager.isInOverworld(victim)).thenReturn(true);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(wantedManager.isWanted(killerId)).thenReturn(true);
        when(wantedManager.getBounty(killerId)).thenReturn(800.0);
        when(playerDataManager.getOverworldDeposit(killerId)).thenReturn(500.0);

        pvpListener.onPlayerDeath(event);

        // 既に指名手配中なら既存の懸賞金でタイマーリセット
        verify(wantedManager).makeWanted(killer, 800.0);
    }

    @Test
    void onDeath_combatLogoutDeath_skipsMoneyProcessing() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(logoutManager.isCombatLogoutDeath(victimId)).thenReturn(true);

        pvpListener.onPlayerDeath(event);

        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());
        verify(combatManager).clearAllData(victimId);
    }

    @Test
    void onDeath_clearsCombatData() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);

        pvpListener.onPlayerDeath(event);

        verify(combatManager).clearCombatData(victimId, killerId);
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
