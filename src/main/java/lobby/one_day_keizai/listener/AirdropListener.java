package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.AirdropManager;
import lobby.one_day_keizai.manager.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * エアドロップイベントハンドラ。
 * - チェスト開封 → クレート確保判定
 * - ワールド移動 → 発光解除判定
 * - 死亡 → 発光解除
 */
public class AirdropListener implements Listener {

    private final AirdropManager airdropManager;
    private final WorldManager worldManager;

    public AirdropListener(AirdropManager airdropManager, WorldManager worldManager) {
        this.airdropManager = airdropManager;
        this.worldManager   = worldManager;
    }

    /** チェストを開けた時にクレートかどうか判定する */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest)) return;

        org.bukkit.Location loc = chest.getBlock().getLocation();

        // クレートだった場合: 通常の開封をキャンセルして確保処理へ
        if (airdropManager.trySecure(player, loc)) {
            event.setCancelled(true);
        }
    }

    /** 安全ワールドへ移動した時に発光を解除する */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (worldManager.isSafeWorld(player.getWorld())) {
            airdropManager.onPlayerEscape(player);
        }
    }

    /** 死亡時に発光を解除する */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        airdropManager.onPlayerDeath(event.getEntity());
    }
}
