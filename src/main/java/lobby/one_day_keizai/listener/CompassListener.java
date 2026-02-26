package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.CompassManager;
import lobby.one_day_keizai.ui.WantedCompassUI;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * コンパスの右クリック処理。
 *  - メインハンドでのみ動作
 *  - ショップコンパス: 即時更新 + 範囲内ならTP
 *  - 賞金首コンパス: 賞金首一覧UIを開く
 *  - バニラ/WEコンパス: キャンセル
 */
public class CompassListener implements Listener {

    private final CompassManager compassManager;
    private final JobManager     jobManager;
    private final WantedCompassUI wantedCompassUI;

    public CompassListener(CompassManager compassManager, JobManager jobManager,
                           WantedCompassUI wantedCompassUI) {
        this.compassManager  = compassManager;
        this.jobManager      = jobManager;
        this.wantedCompassUI = wantedCompassUI;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.COMPASS) return;

        event.setCancelled(true);

        boolean isRightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                            || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!isRightClick) return;

        String type = compassManager.getCompassType(held);

        if (CompassManager.TYPE_SHOP.equals(type)) {
            compassManager.updateShopCompass(event.getPlayer());
            compassManager.handleShopCompassClick(event.getPlayer(), jobManager);
        } else if (CompassManager.TYPE_WANTED.equals(type)) {
            // 賞金首コンパス → 一覧UIを開く
            wantedCompassUI.open(event.getPlayer());
        }
        // バニラコンパスはキャンセルのみ（WE無効化）
    }
}
