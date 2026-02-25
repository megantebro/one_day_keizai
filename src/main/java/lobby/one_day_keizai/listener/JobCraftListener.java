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
     * 鍛冶屋専用クラフトアイテム一覧（剣・防具・盾のみ。ツール類は誰でもクラフト可）
     */
    private static final Set<Material> BLACKSMITH_ONLY_ITEMS = EnumSet.of(
        // 剣
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD,
        // 防具: ヘルメット
        Material.IRON_HELMET, Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        // 防具: チェストプレート
        Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        // 防具: レギンス
        Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        // 防具: ブーツ
        Material.IRON_BOOTS, Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        // 盾・鍛冶設備
        Material.SHIELD, Material.ANVIL, Material.SMITHING_TABLE, Material.GRINDSTONE
    );

    public JobCraftListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /**
     * クラフト完成アイテムチェック。
     * 鍛冶屋専用アイテム → 非鍛冶屋は結果をAIRにする。
     * 農家専用レシピ（小麦俵→エンチャント瓶）→ 非農家は結果をAIRにする。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) return;

        for (HumanEntity viewer : event.getViewers()) {
            if (!(viewer instanceof Player player)) continue;

            // 鍛冶屋専用チェック
            if (BLACKSMITH_ONLY_ITEMS.contains(result.getType())) {
                if (!jobManager.isBlacksmith(player.getUniqueId())) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }
            }

            // 農家専用チェック: エンチャント瓶（バニラにはクラフトレシピなし）
            if (result.getType() == Material.EXPERIENCE_BOTTLE) {
                if (!jobManager.isFarmer(player.getUniqueId())) {
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }
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

        // 鍛冶屋専用チェック
        if (BLACKSMITH_ONLY_ITEMS.contains(result.getType())) {
            if (!jobManager.isBlacksmith(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "このアイテムは鍛冶屋のみクラフトできます。");
                player.sendMessage(ChatColor.YELLOW + "/job select blacksmith で鍛冶屋になれます。");
                return;
            }
        }

        // 農家専用チェック: エンチャント瓶
        if (result.getType() == Material.EXPERIENCE_BOTTLE) {
            if (!jobManager.isFarmer(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "このレシピは農家のみ使用できます。");
                player.sendMessage(ChatColor.YELLOW + "/job select farmer で農家になれます。");
            }
        }
    }
}
