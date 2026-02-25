package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * エンチャントテーブルの使用を鍛冶屋・エンチャンターのみに制限する。
 * <p>
 * 鍛冶屋 (BLACKSMITH) は基本職として使用可能。
 * エンチャンター (ENCHANTER) は上級鍛冶屋として使用可能。
 * それ以外の職業のプレイヤーはエンチャントテーブルを開けない。
 */
public class EnchantTableListener implements Listener {

    private final JobManager jobManager;

    public EnchantTableListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) return;

        Player player = event.getPlayer();

        // isBlacksmith() は鍛冶屋・エンチャンター両方 true を返す
        if (!jobManager.isBlacksmith(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "エンチャントテーブルは鍛冶屋・エンチャンターのみ使用できます。");
            player.sendMessage(ChatColor.YELLOW + "/job select blacksmith で鍛冶屋になれます。");
        }
    }
}
