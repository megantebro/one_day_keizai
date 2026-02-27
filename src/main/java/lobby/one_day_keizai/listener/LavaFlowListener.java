package lobby.one_day_keizai.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * 溶岩の流れを全ワールドで無効化する
 */
public class LavaFlowListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLavaFlow(BlockFromToEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.LAVA) {
            event.setCancelled(true);
        }
    }
}
