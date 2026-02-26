package lobby.one_day_keizai.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * /shop [名前] — ショップエリアへワープ
 * 引数なしで一覧表示。引数ありで指定ショップへ瞬間移動。
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    /** key=コマンド引数(小文字), value={display, Location} */
    private final Map<String, ShopEntry> shops = new LinkedHashMap<>();

    public ShopCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** config から再読み込み */
    public void reload() {
        shops.clear();
        String worldName = plugin.getConfig().getString("shop-world", "economy");
        World world = Bukkit.getWorld(worldName);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shops");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String display = section.getString(key + ".display", key);
            double x = section.getDouble(key + ".x");
            double y = section.getDouble(key + ".y");
            double z = section.getDouble(key + ".z");
            float yaw = (float) section.getDouble(key + ".yaw", 0);
            float pitch = (float) section.getDouble(key + ".pitch", 0);
            Location loc = new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
            shops.put(key.toLowerCase(), new ShopEntry(display, loc));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ使用可能です。");
            return true;
        }

        if (args.length == 0) {
            showList(player);
            return true;
        }

        String key = args[0].toLowerCase();
        ShopEntry entry = shops.get(key);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "ショップ \"" + args[0] + "\" が見つかりません。");
            showList(player);
            return true;
        }

        if (entry.location().getWorld() == null) {
            player.sendMessage(ChatColor.RED + "ショップのワールドが見つかりません。管理者に連絡してください。");
            return true;
        }

        player.teleport(entry.location());
        player.sendMessage(ChatColor.GREEN + "✦ " + entry.display() + " へワープしました！");
        return true;
    }

    private void showList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== ショップ一覧 ===");
        for (Map.Entry<String, ShopEntry> e : shops.entrySet()) {
            player.sendMessage(ChatColor.YELLOW + "/shop " + e.getKey()
                    + ChatColor.GRAY + "  →  " + ChatColor.WHITE + e.getValue().display());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(shops.keySet());
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return Collections.emptyList();
    }

    private record ShopEntry(String display, Location location) {}
}
