package lobby.one_day_keizai.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * 指名手配書アイテム。
 * 安全ワールドに持ち込むと自動換金される。
 */
public class BountyItem {

    private static NamespacedKey bountyKey;

    public static void init(JavaPlugin plugin) {
        bountyKey = new NamespacedKey(plugin, "bounty_amount");
    }

    /**
     * 指名手配書アイテムを生成する。
     * @param amount    懸賞金額
     * @param wantedName 指名手配対象のプレイヤー名
     */
    public static ItemStack create(double amount, String wantedName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.GOLD + "⭐ 指名手配書 [" + wantedName + "]");
        meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "懸賞金: $" + String.format("%.0f", amount),
                ChatColor.GRAY + "安全ワールドに帰還すると自動換金されます"
        ));
        meta.getPersistentDataContainer().set(bountyKey, PersistentDataType.DOUBLE, amount);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * そのアイテムが指名手配書かどうか確認する。
     */
    public static boolean isBountyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(bountyKey, PersistentDataType.DOUBLE);
    }

    /**
     * 指名手配書の懸賞金額を返す。
     */
    public static double getAmount(ItemStack item) {
        if (!isBountyItem(item)) return 0;
        Double value = item.getItemMeta().getPersistentDataContainer()
                .get(bountyKey, PersistentDataType.DOUBLE);
        return value != null ? value : 0;
    }
}
