package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;

/**
 * エンチャントテーブルの使用をエンチャンターのみに制限する。
 * 防具・武器への本棚ボーナスを無効化（Lv.8 上限）。ツールは制限なし。
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

        if (!jobManager.isEnchanter(player.getUniqueId())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (event.getHand() == EquipmentSlot.HAND) {
                player.sendMessage(ChatColor.RED + "エンチャントテーブルはエンチャンターのみ使用できます。");
            }
        }
    }

    /** 本棚ボーナス無効化対象: 防具・武器・盾 */
    private static final Set<Material> ARMOR_AND_WEAPONS = EnumSet.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
        Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        Material.TURTLE_HELMET,
        Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
        Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
        Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        Material.SHIELD
    );

    /**
     * PrepareItemEnchantEvent: 防具・武器のオファーコストを Lv.8 にキャップ。
     * ツールは制限なし。本棚なし (bonus=0) の場合もスキップ。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        if (!(event.getEnchanter() instanceof Player)) return;
        Player player = (Player) event.getEnchanter();
        if (!jobManager.isEnchanter(player.getUniqueId())) return;
        if (event.getEnchantmentBonus() <= 0) return;

        Material itemType = event.getItem().getType();
        if (!ARMOR_AND_WEAPONS.contains(itemType)) return;

        EnchantmentOffer[] offers = event.getOffers();
        if (offers == null) return;
        for (EnchantmentOffer offer : offers) {
            if (offer != null && offer.getCost() > 8) offer.setCost(8);
        }
    }

    /**
     * EnchantItemEvent: setCost() writeback が効かない場合の二重ガード。
     * 防具・武器で expLevelCost > 8 ならキャンセル。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!(event.getEnchanter() instanceof Player)) return;
        Player player = (Player) event.getEnchanter();
        if (!jobManager.isEnchanter(player.getUniqueId())) return;

        Material itemType = event.getItem().getType();
        if (!ARMOR_AND_WEAPONS.contains(itemType)) return;

        if (event.getExpLevelCost() > 8) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "防具・武器のエンチャントは本棚の恩恵を受けられません（上限 Lv.8）。");
        }
    }
}
