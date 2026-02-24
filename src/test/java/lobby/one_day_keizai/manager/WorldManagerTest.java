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
                "economy", "world", 1000.0, 0.8);

        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(player.getName()).thenReturn("TestPlayer");

        lenient().when(safeWorld.getName()).thenReturn("economy");
        lenient().when(overworld.getName()).thenReturn("world");
        lenient().when(overworld.getSpawnLocation()).thenReturn(new Location(overworld, 0, 64, 0));
        lenient().when(safeWorld.getSpawnLocation()).thenReturn(new Location(safeWorld, 0, 64, 0));
    }

    @Test
    void enterOverworld_success() {
        when(player.getWorld()).thenReturn(safeWorld);
        when(economy.getBalance(player)).thenReturn(5000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);

            boolean result = worldManager.enterOverworld(player);

            assertTrue(result);
            verify(economy).withdrawPlayer(player, 1000.0);
            verify(playerDataManager).setOverworldDeposit(playerId, 1000.0);
            verify(player).teleport(overworld.getSpawnLocation());
        }
    }

    @Test
    void enterOverworld_insufficientBalance() {
        when(player.getWorld()).thenReturn(safeWorld);
        when(economy.getBalance(player)).thenReturn(500.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);

            boolean result = worldManager.enterOverworld(player);

            assertFalse(result);
            verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
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
    void returnToSafeWorld_success() {
        when(player.getWorld()).thenReturn(overworld);
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(1000.0);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("economy")).thenReturn(safeWorld);

            boolean result = worldManager.returnToSafeWorld(player);

            assertTrue(result);
            // 80%返金
            verify(economy).depositPlayer(player, 800.0);
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
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(1000.0);

        worldManager.handleOverworldDeath(player);

        verify(playerDataManager).clearOverworldDeposit(playerId);
    }

    @Test
    void consumeDiedInOverworld_returnsTrueAfterDeath() {
        when(playerDataManager.getOverworldDeposit(playerId)).thenReturn(1000.0);

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
