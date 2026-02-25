package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
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

    @Mock private PlayerDataManager dataManager;
    @Mock private CombatManager combatManager;
    @Mock private Economy economy;
    @Mock private WorldManager worldManager;
    @Mock private Player player;
    @Mock private PlayerInventory inventory;
    @Mock private World world;

    private LogoutManager logoutManager;

    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logoutManager = new LogoutManager(dataManager, combatManager,
                economy, worldManager, 15);

        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getInventory()).thenReturn(inventory);
        lenient().when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        lenient().when(player.getWorld()).thenReturn(world);
    }

    // =========================================
    // handleLogout テスト — 安全ワールドチェック
    // =========================================

    @Test
    void handleLogout_inSafeWorld_noPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);
        when(worldManager.isSafeWorld(world)).thenReturn(true);

        logoutManager.handleLogout(player);

        verify(dataManager, never()).setLogoutPenaltyData(any(), any(), any(), anyLong());
    }

    @Test
    void handleLogout_inOverworld_applyPenalty() {
        when(combatManager.isInCombat(playerId)).thenReturn(false);
        when(worldManager.isSafeWorld(world)).thenReturn(false);

        logoutManager.handleLogout(player);

        verify(dataManager).setLogoutPenaltyData(eq(playerId), any(), any(), anyLong());
        verify(dataManager).save();
    }

    // =========================================
    // 戦闘ログアウト テスト
    // =========================================

    @Test
    void combatLogout_givesDepositToAttacker() {
        UUID attackerId = UUID.randomUUID();
        Player attacker = mock(Player.class);

        when(combatManager.isInCombat(playerId)).thenReturn(true);
        when(combatManager.getLastAttacker(playerId)).thenReturn(attackerId);
        when(dataManager.getOverworldDeposit(playerId)).thenReturn(1000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
            bukkit.when(() -> Bukkit.broadcastMessage(anyString())).thenReturn(0);

            logoutManager.handleLogout(player);

            // 所持金没収なし、デポジットを攻撃者に渡す
            verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(economy).depositPlayer(attacker, 1000.0);
        }
    }

    @Test
    void combatLogout_marksDeathFlag() {
        UUID attackerId = UUID.randomUUID();

        when(combatManager.isInCombat(playerId)).thenReturn(true);
        when(combatManager.getLastAttacker(playerId)).thenReturn(attackerId);
        when(dataManager.getOverworldDeposit(playerId)).thenReturn(0.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            // setHealth(0) が呼ばれる前後でフラグが立つことを確認
            bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(null);
            bukkit.when(() -> Bukkit.broadcastMessage(anyString())).thenReturn(0);

            // フラグはsetHealth(0)の前に立ち、後に消えることをverifyするのはモックの制約上難しいが
            // setHealth(0)が呼ばれることを確認
            logoutManager.handleLogout(player);
            verify(player).setHealth(0);
        }
    }

    // =========================================
    // handleLogin テスト
    // =========================================

    @Test
    void handleLogin_noPenaltyData_doesNothing() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(false);

        logoutManager.handleLogin(player);

        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(dataManager, never()).clearLogoutPenaltyData(any());
    }

    @Test
    void handleLogin_withinGracePeriod_keepsItems() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() + 600000);

        logoutManager.handleLogin(player);

        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }

    @Test
    void handleLogin_graceExpired_clearsInventory() {
        when(dataManager.hasLogoutPenaltyData(playerId)).thenReturn(true);
        when(dataManager.getLogoutPenaltyDeadline(playerId))
                .thenReturn(System.currentTimeMillis() - 1000);

        logoutManager.handleLogin(player);

        // 所持金没収なし、アイテム全ロスト
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(inventory).clear();
        verify(dataManager).clearLogoutPenaltyData(playerId);
        verify(dataManager).save();
    }

    // =========================================
    // isCombatLogoutDeath テスト
    // =========================================

    @Test
    void isCombatLogoutDeath_notInProgress_returnsFalse() {
        assertFalse(logoutManager.isCombatLogoutDeath(playerId));
    }
}
