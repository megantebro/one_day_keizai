package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.CompassManager;
import lobby.one_day_keizai.manager.LogoutManager;
import lobby.one_day_keizai.manager.NametagManager;
import lobby.one_day_keizai.manager.WorldManager;
import lobby.one_day_keizai.ui.JobSelectionUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final LogoutManager logoutManager;
    private final NametagManager nametagManager;
    private final WorldManager worldManager;
    private final JobManager jobManager;
    private final CompassManager compassManager;
    private final JavaPlugin plugin;

    public PlayerListener(LogoutManager logoutManager, NametagManager nametagManager,
                          WorldManager worldManager, JobManager jobManager,
                          CompassManager compassManager, JavaPlugin plugin) {
        this.logoutManager = logoutManager;
        this.nametagManager = nametagManager;
        this.worldManager = worldManager;
        this.jobManager = jobManager;
        this.compassManager = compassManager;
        this.plugin = plugin;
    }

    /** コンパスを持っていない場合に普通のコンパスをgiveする */
    private void giveCompassIfMissing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) return;
        }
        player.getInventory().addItem(new ItemStack(Material.COMPASS));
        player.sendMessage(ChatColor.AQUA + "コンパスを受け取りました！");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ネームタグ設定（指名手配はメモリ管理のため、再接続時は通常扱い）
        nametagManager.updateNametag(player, false);

        // ログアウトペナルティチェック
        logoutManager.handleLogin(player);

        // 初回参加メッセージ
        if (!player.hasPlayedBefore()) {
            player.sendMessage(ChatColor.AQUA + "One Day Keizai サーバーへようこそ！");
        }

        // コンパス未所持なら配布（20ticksディレイでインベントリロード完了後に確認）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) giveCompassIfMissing(player);
        }, 20L);

        // 職業未選択なら職業選択UIを表示（20ticksディレイ: ロード完了後に開く）
        if (jobManager.getJob(player.getUniqueId()) == Job.NONE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    JobSelectionUI.open(player);
                }
            }, 20L);
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
        UUID uuid = player.getUniqueId();

        // 指名手配は死亡で解除済みのため、リスポーン時に通常ネームタグへ
        nametagManager.setNormal(player);

        // オーバーワールドで死亡した場合、安全ワールドにリスポーン
        if (worldManager.consumeDiedInOverworld(uuid)) {
            World safeWorld = Bukkit.getWorld(worldManager.getSafeWorldName());
            if (safeWorld != null) {
                event.setRespawnLocation(safeWorld.getSpawnLocation());
            }
        }

        // リスポーン後にコンパス未所持なら配布（ツール返却処理の後に実行）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) giveCompassIfMissing(player);
        }, 5L);
    }
}
