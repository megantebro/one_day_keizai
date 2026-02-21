package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private PlayerDataManager dataManager;
    @Mock private CombatManager combatManager;
    @Mock private CriminalManager criminalManager;
    @Mock private NametagManager nametagManager;
    @Mock private Economy economy;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private World world;

    private LogoutManager logoutManager;

    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logoutManager = new LogoutManager(plugin, dataManager, combatManager, criminalManager,
                nametagManager, economy, 10, 15, 0.33, 15);

        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getInventory()).thenReturn(inventory);
        lenient().when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
    }

    // =========================================
    // handleLogout テスト - ベッド付近チェック
    // =========================================

    @Test
    void handleLogout_noBed_applyPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);
        when(player.getBedSpawnLocation()).thenReturn(null);
        Location playerLoc = new Location(world, 100, 64, 100);
        when(player.getLocation()).thenReturn(playerLoc);

        logoutManager.handleLogout(player);

        verify(dataManager).setLogoutPenaltyData(eq(playerId), any(), any(), anyLong());
        verify(dataManager).save();
    }

    @Test
    void handleLogout_farFromBed_applyPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);

        Location bedLoc = new Location(world, 0, 64, 0);
        Location playerLoc = new Location(world, 100, 64, 100);
        when(player.getBedSpawnLocation()).thenReturn(bedLoc);
        when(player.getLocation()).thenReturn(playerLoc);

        logoutManager.handleLogout(player);

        verify(dataManager).setLogoutPenaltyData(eq(playerId), any(), any(), anyLong());
    }

    @Test
    void handleLogout_nearBed_notStayedLongEnough_applyPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);

        Location bedLoc = new Location(world, 100, 64, 100);
        Location playerLoc = new Location(world, 105, 64, 105);
        when(player.getBedSpawnLocation()).thenReturn(bedLoc);
        when(player.getLocation()).thenReturn(playerLoc);

        // ベッド付近に滞在していない状態
        logoutManager.handleLogout(player);

        verify(dataManager).setLogoutPenaltyData(eq(playerId), any(), any(), anyLong());
    }

    @Test
    void handleLogout_nearBed_stayedLongEnough_noPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);

        Location bedLoc = new Location(world, 100, 64, 100);
        Location playerLoc = new Location(world, 105, 64, 105);
        when(player.getBedSpawnLocation()).thenReturn(bedLoc);
        when(player.getLocation()).thenReturn(playerLoc);

        // ベッド付近に十分な時間滞在した状態をシミュレート
        logoutManager.setBedProximityTimestamp(playerId, System.currentTimeMillis() - 16000);

        logoutManager.handleLogout(player);

        verify(dataManager, never()).setLogoutPenaltyData(any(), any(), any(), anyLong());
    }

    // =========================================
    // handleLogin テスト
    // =========================================

    // =========================================
    // 戦闘ログアウト テスト
    // =========================================

    @Test
    void combatLogout_doesNotIncrementInnocentKillCount() {
        UUID attackerId = UUID.randomUUID();
        Player attacker = mock(Player.class);

        when(combatManager.isInCombat(playerId)).thenReturn(true);
        when(combatManager.getLastAttacker(playerId)).thenReturn(attackerId);
        when(economy.getBalance(player)).thenReturn(1000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);

            logoutManager.handleLogout(player);

            // 金銭奪取は行われる
            verify(economy).withdrawPlayer(player, 330.0);
            verify(economy).depositPlayer(attacker, 330.0);

            // 無実キルカウントは増加しない（悪用防止）
            verify(criminalManager, never()).incrementInnocentKill(any());
        }
    }

    // =========================================
    // handleLogin テスト
    // =========================================

    @Test
    void handleLogin_noPenaltyData_doesNothing() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(false);

        logoutManager.handleLogin(player);

        verify(inventory, never()).clear();
        verify(dataManager, never()).clearLogoutPenaltyData(any());
    }

    @Test
    void handleLogin_withinGracePeriod_keepsItems() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() + 600000);

        logoutManager.handleLogin(player);

        verify(inventory, never()).clear();
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }

    @Test
    void handleLogin_graceExpired_criminal_killsPlayer() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() - 1000);
        when(criminalManager.isCriminal(playerId)).thenReturn(true);

        logoutManager.handleLogin(player);

        verify(player).setHealth(0);
        verify(economy, never()).withdrawPlayer(eq(player), anyDouble());
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }

    @Test
    void handleLogin_graceExpired_nonCriminal_losesMoneyOnly() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() - 1000);
        when(criminalManager.isCriminal(playerId)).thenReturn(false);
        when(economy.getBalance(player)).thenReturn(1000.0);

        logoutManager.handleLogin(player);

        // 死亡しない
        verify(player, never()).setHealth(0);
        // 所持金の33%が没収される
        verify(economy).withdrawPlayer(player, 330.0);
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }
}
