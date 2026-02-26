package lobby.one_day_keizai.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * WorldEdit などによるコンパスのクリックイベントをキャンセルする。
 * 右クリック/左クリック時にコンパスを持っていれば無条件でキャンセル。
 * （当プラグインのコンパス更新はタイマーベースなのでクリック不要）
 */
public class CompassListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassClick(PlayerInteractEvent event) {
        ItemStack main = event.getItem();
        if (main != null && main.getType() == Material.COMPASS) {
            event.setCancelled(true);
        }
    }
}
