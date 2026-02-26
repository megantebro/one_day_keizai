package lobby.one_day_keizai.command;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
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
 * /shop        — 自分の職業ショップへワープ
 * /shop list   — ショップ一覧表示
 * /shop <名前> — 指定ショップへワープ
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final JobManager jobManager;

    /** key=コマンド引数(小文字), value={display, Location} */
    private final Map<String, ShopEntry> shops = new LinkedHashMap<>();

    /** 職業 → ショップキー のマッピング */
    private static final Map<Job, String> JOB_SHOP_MAP = Map.of(
        Job.FARMER,           "farmer",
        Job.BLACKSMITH,       "blacksmith",
        Job.ENCHANTER,        "blacksmith",   // 上級鍛冶屋
        Job.MERCHANT,         "merchant",
        Job.WEALTHY_MERCHANT, "merchant"      // 上級商人
    );

    public ShopCommand(JavaPlugin plugin, JobManager jobManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        reload();
    }

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
            float yaw   = (float) section.getDouble(key + ".yaw", 0);
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

        // /shop list
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            showList(player);
            return true;
        }

        // /shop <名前> (直接指定)
        if (args.length > 0) {
            teleportTo(player, args[0].toLowerCase());
            return true;
        }

        // /shop — 自分の職業ショップへ
        Job job = jobManager.getJob(player.getUniqueId());
        String shopKey = JOB_SHOP_MAP.get(job);

        if (shopKey == null) {
            player.sendMessage(ChatColor.RED + "職業を選択すると自分のショップへワープできます。");
            player.sendMessage(ChatColor.YELLOW + "/job select で職業を選んでください。");
            return true;
        }

        teleportTo(player, shopKey);
        return true;
    }

    private void teleportTo(Player player, String key) {
        ShopEntry entry = shops.get(key);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "ショップ \"" + key + "\" が見つかりません。");
            showList(player);
            return;
        }
        if (entry.location().getWorld() == null) {
            player.sendMessage(ChatColor.RED + "ショップのワールドが見つかりません。管理者に連絡してください。");
            return;
        }
        player.teleport(entry.location());
        player.sendMessage(ChatColor.GREEN + "✦ " + entry.display() + " へワープしました！");
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
            completions.add("list");
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return Collections.emptyList();
    }

    private record ShopEntry(String display, Location location) {}
}
