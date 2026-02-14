package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final CriminalManager criminalManager;
    private final ProtectionManager protectionManager;
    private final LogoutManager logoutManager;
    private final NametagManager nametagManager;

    public PlayerListener(CriminalManager criminalManager, ProtectionManager protectionManager,
                          LogoutManager logoutManager, NametagManager nametagManager) {
        this.criminalManager = criminalManager;
        this.protectionManager = protectionManager;
        this.logoutManager = logoutManager;
        this.nametagManager = nametagManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ネームタグ設定
        nametagManager.updateNametag(player,
                criminalManager.isCriminal(player.getUniqueId()), false);

        // ログアウトペナルティチェック
        logoutManager.handleLogin(player);

        // 初回参加チェック
        if (!player.hasPlayedBefore()) {
            giveStarterKit(player);
            player.sendMessage(ChatColor.AQUA + "One Day Keizai サーバーへようこそ！");
            player.sendMessage(ChatColor.AQUA + "初期装備として石ピッケルと石斧を支給しました。");
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

        // リスポーン保護を付与（1tick後に実行して確実にリスポーン後に適用）
        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("one_day_keizai"),
                () -> {
                    protectionManager.applyProtection(player);
                    giveStarterKit(player);
                    player.sendMessage(ChatColor.GREEN + "リスポーン保護が付与されました（10分間）。");
                    player.sendMessage(ChatColor.GREEN + "保護中は攻撃も被攻撃もできません。");
                    player.sendMessage(ChatColor.AQUA + "初期装備として石ピッケルと石斧を支給しました。");
                }, 1L);
    }

    private void giveStarterKit(Player player) {
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE, 1));
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE, 1));
    }
}
