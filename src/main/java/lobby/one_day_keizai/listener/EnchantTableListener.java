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

import java.util.EnumSet;
import java.util.Set;

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

    /** 本棚ボーナス無効化の対象: 防具・武器のみ */
    private static final Set<Material> ARMOR_AND_WEAPONS = EnumSet.of(
        // 剣
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        // 防具: ヘルメット
        Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
        Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        Material.TURTLE_HELMET,
        // 防具: チェストプレート
        Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        // 防具: レギンス
        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
        Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        // 防具: ブーツ
        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
        Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        // 盾
        Material.SHIELD
    );

    /**
     * エンチャンター向け本棚ボーナス無効化。
     * 防具・武器のエンチャントに限り、本棚ありでもレベル上限を 8 にキャップ。
     * ツール類（ピッケル・斧・シャベル等）は本棚の恩恵を受けられる。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!(event.getEnchanter() instanceof Player)) return;
        Player player = (Player) event.getEnchanter();
        if (!jobManager.isEnchanter(player.getUniqueId())) return;
        if (event.getEnchantmentBonus() <= 0) return;

        // ツール類は制限なし
        Material itemType = event.getItem().getType();
        if (!ARMOR_AND_WEAPONS.contains(itemType)) return;

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
            player.sendMessage(ChatColor.YELLOW + "防具・武器のエンチャントは本棚の恩恵を受けられません（上限 Lv.8）。");
        }
    }
}
