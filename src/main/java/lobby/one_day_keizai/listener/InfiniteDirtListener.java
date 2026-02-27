package lobby.one_day_keizai.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 無限の土の棒: 右クリックで土を64個インベントリに追加する
 * /dirtrod [player] でOP専用give
 */
public class InfiniteDirtListener implements Listener, CommandExecutor {

    private static final String PDC_VALUE = "infinite_dirt";
    private final NamespacedKey key;

    public InfiniteDirtListener(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "item_type");
    }

    /** 無限の土の棒アイテムを生成 */
    public ItemStack createDirtRod() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "無限の土の棒");
        meta.setLore(List.of(
            ChatColor.GRAY + "右クリックで土を64個補充",
            ChatColor.DARK_GRAY + "特別アイテム"
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, PDC_VALUE);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isDirtRod(ItemStack item) {
        if (item == null || item.getType() != Material.STICK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return PDC_VALUE.equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.hasItem()) return;

        ItemStack item = event.getItem();
        if (!isDirtRod(item)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        player.sendMessage(ChatColor.YELLOW + "土を64個補充しました！");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("one_day_keizai.dirtrod")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("使い方: /dirtrod <player>");
            return true;
        }

        target.getInventory().addItem(createDirtRod());
        target.sendMessage(ChatColor.GOLD + "無限の土の棒を受け取りました！");
        if (!sender.getName().equals(target.getName())) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " に無限の土の棒を渡しました。");
        }
        return true;
    }
}
