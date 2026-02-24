package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class JobCraftListener implements Listener {

    private final JobManager jobManager;

    /**
     * 鍛冶屋専用クラフトアイテム一覧（鉄・金・ダイヤ・ネザライトのツール/装備）
     */
    private static final Set<Material> BLACKSMITH_ONLY_ITEMS = EnumSet.of(
        // 鉄
        Material.IRON_SWORD, Material.IRON_SHOVEL, Material.IRON_PICKAXE,
        Material.IRON_AXE, Material.IRON_HOE,
        Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
        // 金
        Material.GOLDEN_SWORD, Material.GOLDEN_SHOVEL, Material.GOLDEN_PICKAXE,
        Material.GOLDEN_AXE, Material.GOLDEN_HOE,
        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
        // ダイヤ
        Material.DIAMOND_SWORD, Material.DIAMOND_SHOVEL, Material.DIAMOND_PICKAXE,
        Material.DIAMOND_AXE, Material.DIAMOND_HOE,
        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
        // ネザライト（スミスingテーブルで使うが念のため）
        Material.NETHERITE_SWORD, Material.NETHERITE_SHOVEL, Material.NETHERITE_PICKAXE,
        Material.NETHERITE_AXE, Material.NETHERITE_HOE,
        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
        // その他鍛冶専用
        Material.IRON_INGOT,  // 精錬は別途。クラフトレシピはシールド等に使用
        Material.SHIELD, Material.ANVIL, Material.SMITHING_TABLE, Material.GRINDSTONE
    );

    public JobCraftListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /**
     * クラフト完成アイテムが鍛冶屋専用かチェックし、
     * 非鍛冶屋の場合はクラフト枠を空にして制限メッセージを送る。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) return;
        if (!BLACKSMITH_ONLY_ITEMS.contains(result.getType())) return;

        for (HumanEntity viewer : event.getViewers()) {
            if (!(viewer instanceof Player player)) continue;
            if (!jobManager.isBlacksmith(player.getUniqueId())) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                // メッセージは CraftItemEvent で送る（PrepareでのメッセージはUI更新ごとに連発するため）
                return;
            }
        }
    }

    /**
     * クラフト実行時にも再度チェックしてメッセージを送る。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        if (!BLACKSMITH_ONLY_ITEMS.contains(result.getType())) return;

        if (!jobManager.isBlacksmith(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "このアイテムは鍛冶屋のみクラフトできます。");
            player.sendMessage(ChatColor.YELLOW + "/job select blacksmith で鍛冶屋になれます。");
        }
    }
}
