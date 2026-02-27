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
import org.bukkit.entity.Entity;
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
        Job.CAPITALIST,       "farmer",       // 上級農家
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

        // ===== コマンドブロック / コンソールからの呼び出し =====
        // /shop <player/@p>           → プレイヤーを職業ショップへTP
        // /shop <player/@p> <shopKey> → プレイヤーを指定ショップへTP
        if (!(sender instanceof Player)) {
            if (args.length == 0) {
                sender.sendMessage("使い方: /shop <player> [shopKey]");
                return true;
            }
            Player target = resolvePlayer(sender, args[0]);
            if (target == null) {
                sender.sendMessage("プレイヤーが見つかりません: " + args[0]);
                return true;
            }
            if (args.length >= 2) {
                teleportTo(target, args[1].toLowerCase());
            } else {
                teleportToJobShop(target);
            }
            return true;
        }

        // ===== プレイヤーからの呼び出し =====
        Player player = (Player) sender;

        // /shop list
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            showList(player);
            return true;
        }

        // /shop <名前> (直接指定) — OP専用
        if (args.length > 0) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "直接指定はOP専用です。");
                return true;
            }
            teleportTo(player, args[0].toLowerCase());
            return true;
        }

        // /shop — 自分の職業ショップへ
        teleportToJobShop(player);
        return true;
    }

    private void teleportToJobShop(Player player) {
        Job job = jobManager.getJob(player.getUniqueId());
        String shopKey = JOB_SHOP_MAP.get(job);

        if (shopKey == null) {
            player.sendMessage(ChatColor.RED + "職業を選択すると自分のショップへワープできます。");
            player.sendMessage(ChatColor.YELLOW + "/job select で職業を選んでください。");
            return;
        }

        teleportTo(player, shopKey);
    }

    /** @p などのセレクターまたはプレイヤー名を解決して Player を返す */
    private Player resolvePlayer(CommandSender sender, String nameOrSelector) {
        // セレクター（@p, @a, @r 等）を試みる
        if (nameOrSelector.startsWith("@")) {
            try {
                List<org.bukkit.entity.Entity> entities = Bukkit.selectEntities(sender, nameOrSelector);
                for (org.bukkit.entity.Entity e : entities) {
                    if (e instanceof Player p) return p;
                }
                return null;
            } catch (IllegalArgumentException ignored) {}
        }
        return Bukkit.getPlayer(nameOrSelector);
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
            List<String> completions = new ArrayList<>();
            completions.add("list");
            if (sender.isOp()) completions.addAll(shops.keySet());
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return Collections.emptyList();
    }

    private record ShopEntry(String display, Location location) {}
}
