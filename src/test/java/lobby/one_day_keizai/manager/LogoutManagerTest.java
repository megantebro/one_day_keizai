package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
    void handleLogin_graceExpired_clearsInventory() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() - 1000);

        logoutManager.handleLogin(player);

        verify(inventory).clear();
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }
}
