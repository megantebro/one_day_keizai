package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.EnumSet;
import java.util.Set;

public class JobFarmListener implements Listener {

    private final JobManager jobManager;
    private final String safeWorldName;

    /**
     * 農家専用の農作物ブロック一覧（設置・植え付けに使うアイテムのブロック形式）
     * 種・芽・苗など「設置」に対応するブロック素材を列挙する。
     */
    private static final Set<Material> FARMER_ONLY_CROPS = EnumSet.of(
        Material.WHEAT,           // 小麦の種を植えると生成されるブロック (CROPS)
        Material.WHEAT_SEEDS,     // 手に持って右クリックで植える
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOT_SEEDS,
        Material.MELON_SEEDS,
        Material.PUMPKIN_SEEDS,
        Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER_SEEDS,
        Material.PITCHER_POD,
        Material.NETHER_WART,
        Material.COCOA,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS,
        Material.FARMLAND          // 農地を耕す行為自体はOKだが農作物植えで制限
    );

    /**
     * 木材系（サプリングの設置）は全員OK — このセットに含めない。
     * 農作物のみを農家専用とする。
     */
    public JobFarmListener(JobManager jobManager, String safeWorldName) {
        this.jobManager = jobManager;
        this.safeWorldName = safeWorldName;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        // 安全ワールドのみ制限を適用
        if (!player.getWorld().getName().equals(safeWorldName)) return;

        Material placed = event.getBlockPlaced().getType();
        if (!FARMER_ONLY_CROPS.contains(placed)) return;

        // 農家以外はキャンセル
        if (!jobManager.isFarmer(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "農作物の栽培は農家のみ可能です。");
            player.sendMessage(ChatColor.YELLOW + "/job select farmer で農家になれます。");
        }
    }
}
