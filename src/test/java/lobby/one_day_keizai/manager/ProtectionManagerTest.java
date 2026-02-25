package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.TestHelper;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtectionManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private NametagManager nametagManager;
    @Mock private Player player;
    @Mock private Server server;
    @Mock private BukkitScheduler scheduler;

    private ProtectionManager protectionManager;

    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Bukkit.server にモックを設定
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        TestHelper.setupBukkitServer(server);

        protectionManager = new ProtectionManager(plugin, nametagManager, 600);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardownBukkitServer();
    }

    @Test
    void isProtected_noProtection_returnsFalse() {
        assertFalse(protectionManager.isProtected(playerId));
    }

    @Test
    void isProtected_afterManualSet_returnsTrue() {
        setProtectedUntil(playerId, System.currentTimeMillis() + 600_000);
        assertTrue(protectionManager.isProtected(playerId));
    }

    @Test
    void isProtected_expired_returnsFalse() {
        setProtectedUntil(playerId, System.currentTimeMillis() - 1000);
        assertFalse(protectionManager.isProtected(playerId));
    }

    @Test
    void isProtected_differentPlayer_returnsFalse() {
        setProtectedUntil(playerId, System.currentTimeMillis() + 600_000);
        UUID otherId = UUID.randomUUID();
        assertFalse(protectionManager.isProtected(otherId));
    }

    @Test
    void removeProtection_makesPlayerUnprotected() {
        setProtectedUntil(playerId, System.currentTimeMillis() + 600_000);
        assertTrue(protectionManager.isProtected(playerId));

        // Bukkit.getPlayer(uuid)がnullを返すので、nametag更新はスキップされるが保護解除は動作する
        protectionManager.removeProtection(playerId);
        assertFalse(protectionManager.isProtected(playerId));
    }

    @SuppressWarnings("unchecked")
    private void setProtectedUntil(UUID uuid, long until) {
        try {
            var field = ProtectionManager.class.getDeclaredField("protectedUntil");
            field.setAccessible(true);
            var map = (java.util.Map<UUID, Long>) field.get(protectionManager);
            map.put(uuid, until);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
