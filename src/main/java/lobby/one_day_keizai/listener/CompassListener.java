package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.CompassManager;
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
 *  - プラグイン製ショップコンパス:
 *      1. コンパス針を即時更新（ワールドに応じた方向へ）
 *      2. 範囲内ならTP (front付近→back入場 / back付近→front退場)
 *  - その他コンパス（バニラ/WE）: キャンセル（WE移動無効化）
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
        // メインハンドのみ
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.COMPASS) return;

        event.setCancelled(true);

        boolean isRightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                            || event.getAction() == Action.RIGHT_CLICK_BLOCK;

        if (!isRightClick) return;

        String type = compassManager.getCompassType(held);

        if (CompassManager.TYPE_SHOP.equals(type)) {
            // 1. コンパス針を即時更新
            compassManager.updateShopCompass(event.getPlayer());
            // 2. 範囲チェック → TP or 更新のみ
            compassManager.handleShopCompassClick(event.getPlayer(), jobManager);
        }
        // TYPE_WANTED や バニラコンパスはキャンセルのみ（WE無効化）
    }
}
