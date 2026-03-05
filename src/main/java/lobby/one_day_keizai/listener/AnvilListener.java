package lobby.one_day_keizai.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 修繕不可タグ（no_repair）が付いたアイテムの金床修繕をブロックする。
 * リネームやエンチャント合成は許可する。
 */
public class AnvilListener implements Listener {

    private final NamespacedKey noRepairKey;

    public AnvilListener(NamespacedKey noRepairKey) {
        this.noRepairKey = noRepairKey;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first  = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);

        if (first == null || !hasNoRepairTag(first)) return;
        if (second == null || second.getType() == Material.AIR) return;

        // スロット1に何かある = 修繕または合成の試み
        // エンチャント本のみ許可（エンチャント移植はOK）
        if (second.getType() == Material.ENCHANTED_BOOK) return;

        // それ以外（同素材での修繕など）はブロック
        event.setResult(null);
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player) {
                player.sendMessage(ChatColor.RED + "✘ このアイテムは金床で修繕できません。");
            }
        }
    }

    private boolean hasNoRepairTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(noRepairKey, PersistentDataType.BYTE);
    }
}
