package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
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

        // エンチャンター（上級職）のみ使用可能
        if (!jobManager.isEnchanter(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "エンチャントテーブルはエンチャンターのみ使用できます。");
            player.sendMessage(ChatColor.YELLOW + "/job promote でエンチャンターに昇格できます。");
        }
    }

    /**
     * エンチャンター向け本棚ボーナス無効化。
     * 本棚がある環境でもレベル上限を 8 にキャップする。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!(event.getEnchanter() instanceof Player)) return;
        Player player = (Player) event.getEnchanter();
        if (!jobManager.isEnchanter(player.getUniqueId())) return;

        // 本棚ボーナスが存在する場合にオファーコストを 8 以下にキャップ
        if (event.getEnchantmentBonus() <= 0) return;

        EnchantmentOffer[] offers = event.getOffers();
        if (offers == null) return;
        boolean capped = false;
        for (EnchantmentOffer offer : offers) {
            if (offer != null && offer.getCost() > 8) {
                offer.setCost(8);
                capped = true;
            }
        }
        if (capped) {
            player.sendMessage(ChatColor.YELLOW + "エンチャンターは本棚の恩恵を受けられません（上限 Lv.8）。");
        }
    }
}
