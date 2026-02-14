package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.*;
import net.milkbowl.vault.economy.Economy;
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

    @Mock private Player victim;
    @Mock private Player killer;

    private PvPListener pvpListener;

    private final UUID victimId = UUID.randomUUID();
    private final UUID killerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pvpListener = new PvPListener(economy, criminalManager, combatManager,
                protectionManager, debtManager, nametagManager, 0.33);

        lenient().when(victim.getUniqueId()).thenReturn(victimId);
        lenient().when(killer.getUniqueId()).thenReturn(killerId);
        lenient().when(victim.getName()).thenReturn("Victim");
        lenient().when(killer.getName()).thenReturn("Killer");
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
    void onDeath_noKiller_doesNothing() {
        PlayerDeathEvent event = createDeathEvent(null);

        pvpListener.onPlayerDeath(event);

        verifyNoInteractions(economy);
        verifyNoInteractions(combatManager);
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
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(true);
        when(criminalManager.incrementInnocentKill(killerId)).thenReturn(false);
        when(criminalManager.getInnocentKillCount(killerId)).thenReturn(1);

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
    void onDeath_clearsCombatData() {
        PlayerDeathEvent event = createDeathEvent(killer);
        when(economy.getBalance(victim)).thenReturn(0.0);
        when(debtManager.isDebtor(victimId)).thenReturn(false);
        when(criminalManager.isCriminal(victimId)).thenReturn(false);
        when(combatManager.isInnocentKill(killerId, victimId)).thenReturn(false);

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
