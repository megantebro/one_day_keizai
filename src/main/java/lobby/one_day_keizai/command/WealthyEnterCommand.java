package lobby.one_day_keizai.command;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * /wealthyenter <player|@p>
 *
 * コマンドブロック専用コマンド。
 * ボタンでコマブロを起動 → 豪商(WEALTHY_MERCHANT)なら豪商エリアへTP、それ以外は弾く。
 */
public class WealthyEnterCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JobManager jobManager;

    public WealthyEnterCommand(JavaPlugin plugin, JobManager jobManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // プレイヤーが直接打った場合はOP以外弾く（コマンドブロックはBlockCommandSender）
        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "このコマンドはコマンドブロック専用です。");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("使い方: /wealthyenter <player|@p>");
            return true;
        }

        // @p などセレクターまたはプレイヤー名を解決
        Player target = resolvePlayer(sender, args[0]);
        if (target == null) {
            sender.sendMessage("プレイヤーが見つかりません: " + args[0]);
            return true;
        }

        Job job = jobManager.getJob(target.getUniqueId());

        if (job != Job.WEALTHY_MERCHANT) {
            // 拒否
            target.sendMessage(ChatColor.RED + "✘ 豪商専用エリアです。豪商に昇格してください。");
            target.sendMessage(ChatColor.GRAY + "（現在の職業: " + jobDisplayName(job) + "）");
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        // 豪商エリアへテレポート
        String worldName = plugin.getConfig().getString("wealthy-shop-world", "economy");
        double x = plugin.getConfig().getDouble("wealthy-shop-x", -479.5);
        double y = plugin.getConfig().getDouble("wealthy-shop-y", 111.0);
        double z = plugin.getConfig().getDouble("wealthy-shop-z", -460.5);
        float yaw   = (float) plugin.getConfig().getDouble("wealthy-shop-yaw", 90.0);
        float pitch = (float) plugin.getConfig().getDouble("wealthy-shop-pitch", 0.0);

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            target.sendMessage(ChatColor.RED + "豪商エリアのワールドが見つかりません。管理者に連絡してください。");
            return true;
        }

        Location dest = new Location(world, x, y, z, yaw, pitch);
        target.teleport(dest);
        target.sendMessage(ChatColor.GOLD + "✦ 豪商専用ショップエリアへようこそ！");
        target.playSound(dest, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

        return true;
    }

    private Player resolvePlayer(CommandSender sender, String nameOrSelector) {
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

    private String jobDisplayName(Job job) {
        return switch (job) {
            case NONE             -> "無職";
            case FARMER           -> "農家";
            case BLACKSMITH       -> "鍛冶屋";
            case MERCHANT         -> "商人";
            case CAPITALIST       -> "資本家";
            case ENCHANTER        -> "エンチャンター";
            case WEALTHY_MERCHANT -> "豪商";
        };
    }
}
