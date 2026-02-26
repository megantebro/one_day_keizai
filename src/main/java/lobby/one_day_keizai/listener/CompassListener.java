package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.CompassManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * コンパスの右クリック処理。
 *  - プラグイン製ショップコンパス: 職業チェック → TP or 拒否
 *  - それ以外のコンパス（バニラ/WE）: キャンセル（WE移動無効化）
 */
public class CompassListener implements Listener {

    private final CompassManager compassManager;
    private final JobManager jobManager;

    public CompassListener(CompassManager compassManager, JobManager jobManager) {
        this.compassManager = compassManager;
        this.jobManager     = jobManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassClick(PlayerInteractEvent event) {
        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.COMPASS) return;

        // 右クリックのみ処理（左クリックはキャンセルだけ）
        boolean isRightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                            || event.getAction() == Action.RIGHT_CLICK_BLOCK;

        String type = compassManager.getCompassType(held);

        if (CompassManager.TYPE_SHOP.equals(type) && isRightClick) {
            // ショップコンパス右クリック: 職業チェック + TP
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                compassManager.handleShopCompassClick(event.getPlayer(), jobManager);
            }
        } else {
            // バニラコンパス or 賞金首コンパス or 左クリック: WE無効化のためキャンセル
            event.setCancelled(true);
        }
    }
}
