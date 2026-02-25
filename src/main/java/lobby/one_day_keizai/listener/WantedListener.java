package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.WantedManager;
import lobby.one_day_keizai.manager.WorldManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * 指名手配システム補助リスナー。
 * - オーバーワールドでのベッド使用を禁止
 * - 安全ワールドに入った時に未払い懸賞金を自動入金
 */
public class WantedListener implements Listener {

    private final WorldManager worldManager;
    private final WantedManager wantedManager;

    public WantedListener(WorldManager worldManager, WantedManager wantedManager) {
        this.worldManager = worldManager;
        this.wantedManager = wantedManager;
    }

    /**
     * オーバーワールドでのベッド使用をキャンセル。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!worldManager.isSafeWorld(player.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "このワールドではベッドを使用できません。");
        }
    }

    /**
     * 安全ワールドに入った時に未払い懸賞金を入金する。
     * 時効成立後に pendingPayouts に積まれた分をここで精算する。
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!worldManager.isSafeWorld(player.getWorld())) return;

        wantedManager.collectPendingPayout(player);
    }
}
