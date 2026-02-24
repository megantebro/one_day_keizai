package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final CriminalManager criminalManager;
    private final ProtectionManager protectionManager;
    private final LogoutManager logoutManager;
    private final NametagManager nametagManager;
    private final WorldManager worldManager;

    public PlayerListener(CriminalManager criminalManager, ProtectionManager protectionManager,
                          LogoutManager logoutManager, NametagManager nametagManager,
                          WorldManager worldManager) {
        this.criminalManager = criminalManager;
        this.protectionManager = protectionManager;
        this.logoutManager = logoutManager;
        this.nametagManager = nametagManager;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ネームタグ設定
        nametagManager.updateNametag(player,
                criminalManager.isCriminal(player.getUniqueId()), false);

        // ログアウトペナルティチェック
        logoutManager.handleLogin(player);

        // 初回参加メッセージ
        if (!player.hasPlayedBefore()) {
            player.sendMessage(ChatColor.AQUA + "One Day Keizai サーバーへようこそ！");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        logoutManager.handleLogout(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // オーバーワールドで死亡した場合、安全ワールドにリスポーン
        if (worldManager.consumeDiedInOverworld(player.getUniqueId())) {
            World safeWorld = Bukkit.getWorld(worldManager.getSafeWorldName());
            if (safeWorld != null) {
                event.setRespawnLocation(safeWorld.getSpawnLocation());
            }
        }
    }
}
