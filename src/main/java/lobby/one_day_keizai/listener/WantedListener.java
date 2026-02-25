package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.item.BountyItem;
import lobby.one_day_keizai.manager.WorldManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 指名手配システム補助リスナー。
 * - オーバーワールドでのベッド使用を禁止
 * - 安全ワールドに入った時に懸賞金アイテムを自動換金
 */
public class WantedListener implements Listener {

    private final WorldManager worldManager;
    private final Economy economy;

    public WantedListener(WorldManager worldManager, Economy economy) {
        this.worldManager = worldManager;
        this.economy = economy;
    }

    /**
     * オーバーワールドでのベッド使用をキャンセル。
     * スポーン地点が変わらないようにする。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!worldManager.isSafeWorld(player.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "このワールドではベッドを使用できません。");
        }
    }

    /**
     * 安全ワールドに入った時に懸賞金アイテムを自動換金する。
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // 安全ワールドに入った場合のみ処理
        if (!worldManager.isSafeWorld(player.getWorld())) return;

        exchangeBountyItems(player);
    }

    private void exchangeBountyItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        double totalBounty = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (BountyItem.isBountyItem(item)) {
                totalBounty += BountyItem.getAmount(item) * item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (totalBounty > 0) {
            economy.depositPlayer(player, totalBounty);
            player.sendMessage(ChatColor.GOLD + "指名手配書を換金しました: +"
                    + String.format("%.0f", totalBounty));
        }
    }
}
