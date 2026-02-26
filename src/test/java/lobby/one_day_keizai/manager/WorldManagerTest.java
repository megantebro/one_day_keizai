package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
class WorldManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private Economy economy;
    @Mock private PlayerDataManager playerDataManager;
    @Mock private Player player;
    @Mock private World safeWorld;
    @Mock private World overworld;

    private WorldManager worldManager;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        worldManager = new WorldManager(plugin, economy, playerDataManager,
                "economy", "world");

        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(player.getName()).thenReturn("TestPlayer");

        lenient().when(safeWorld.getName()).thenReturn("economy");
        lenient().when(overworld.getName()).thenReturn("world");
        lenient().when(overworld.getSpawnLocation()).thenReturn(new Location(overworld, 0, 64, 0));
        lenient().when(safeWorld.getSpawnLocation()).thenReturn(new Location(safeWorld, 0, 64, 0));
        lenient().when(overworld.getMaxHeight()).thenReturn(320);
        lenient().when(player.getLocation()).thenReturn(new Location(safeWorld, 0, 64, 0));
    }

    @Test
    void calculateEntryFee_tenPercentOfBalance() {
        // 通常: 所持金の10%
        assertEquals(500.0, worldManager.calculateEntryFee(5000.0), 0.01);
        assertEquals(1000.0, worldManager.calculateEntryFee(10000.0), 0.01);
    }

    @Test
    void calculateEntryFee_cappedAt20000() {
        // 上限2万
        assertEquals(20000.0, worldManager.calculateEntryFee(300000.0), 0.01);
        assertEquals(20000.0, worldManager.calculateEntryFee(200000.0), 0.01);
        // ちょうど境界: 200000 * 0.1 = 20000
        assertEquals(20000.0, worldManager.calculateEntryFee(200000.0), 0.01);
    }

    @Test
    void calculateEntryFee_zeroBalance() {
        assertEquals(0.0, worldManager.calculateEntryFee(0.0), 0.01);
    }

    @Test
    void enterOverworld_success() {
        when(player.getWorld()).thenReturn(safeWorld);
        when(economy.getBalance(player)).thenReturn(5000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);

            boolean result = worldManager.enterOverworld(player);

            assertTrue(result);
            // 入場料 = 5000 * 10% = 500
            verify(economy).withdrawPlayer(player, 500.0);
            verify(playerDataManager).setOverworldDeposit(playerId, 500.0);
            // スポーン付近ランダム位置なのでany(Location)で検証
            verify(player).teleport(any(Location.class));
        }
    }

    @Test
    void enterOverworld_largBalance_capped() {
        when(player.getWorld()).thenReturn(safeWorld);
        when(economy.getBalance(player)).thenReturn(300000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);

            boolean result = worldManager.enterOverworld(player);

            assertTrue(result);
            // 入場料上限: 20000
            verify(economy).withdrawPlayer(player, 20000.0);
            verify(playerDataManager).setOverworldDeposit(playerId, 20000.0);
        }
    }

    @Test
    void enterOverworld_alreadyInOverworld() {
        when(player.getWorld()).thenReturn(overworld);

        boolean result = worldManager.enterOverworld(player);

        assertFalse(result);
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    @Test
    void returnToSafeWorld_success_fullRefund() {
        when(player.getWorld()).thenReturn(overworld);
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(500.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("economy")).thenReturn(safeWorld);

            boolean result = worldManager.returnToSafeWorld(player);

            assertTrue(result);
            // 100%返金
            verify(economy).depositPlayer(player, 500.0);
            verify(playerDataManager).clearOverworldDeposit(playerId);
            verify(player).teleport(safeWorld.getSpawnLocation());
        }
    }

    @Test
    void returnToSafeWorld_notInOverworld() {
        when(player.getWorld()).thenReturn(safeWorld);

        boolean result = worldManager.returnToSafeWorld(player);

        assertFalse(result);
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());
    }

    @Test
    void handleOverworldDeath_confiscatesDeposit() {
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(500.0);

        worldManager.handleOverworldDeath(player);

        verify(playerDataManager).clearOverworldDeposit(playerId);
    }

    @Test
    void consumeDiedInOverworld_returnsTrueAfterDeath() {
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(500.0);

        worldManager.handleOverworldDeath(player);

        assertTrue(worldManager.consumeDiedInOverworld(playerId));
        // 2回目はfalse
        assertFalse(worldManager.consumeDiedInOverworld(playerId));
    }

    @Test
    void isInOverworld_correct() {
        when(player.getWorld()).thenReturn(overworld);
        assertTrue(worldManager.isInOverworld(player));

        when(player.getWorld()).thenReturn(safeWorld);
        assertFalse(worldManager.isInOverworld(player));
    }

    @Test
    void isSafeWorld_correct() {
        assertTrue(worldManager.isSafeWorld(safeWorld));
        assertFalse(worldManager.isSafeWorld(overworld));
    }
}
