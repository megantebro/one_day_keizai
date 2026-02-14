package lobby.one_day_keizai;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;

/**
 * テストヘルパー: Bukkit.server にモックServerを設定する。
 */
public class TestHelper {

    public static void setupBukkitServer(Server server) {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Bukkit.server", e);
        }
    }

    public static void teardownBukkitServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception e) {
            // ignore
        }
    }
}
