package lobby.one_day_keizai.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * /wealthyexit <player|@p>
 *
 * 豪商エリア内のコマンドブロック専用。
 * ボタンを押すと通常商人エリア側へ戻す。
 */
public class WealthyExitCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public WealthyExitCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "このコマンドはコマンドブロック専用です。");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("使い方: /wealthyexit <player|@p>");
            return true;
        }

        Player target = resolvePlayer(sender, args[0]);
        if (target == null) {
            sender.sendMessage("プレイヤーが見つかりません: " + args[0]);
            return true;
        }

        String worldName = plugin.getConfig().getString("wealthy-shop-world", "economy");
        double x   = plugin.getConfig().getDouble("wealthy-exit-x",   -479.5);
        double y   = plugin.getConfig().getDouble("wealthy-exit-y",    111.0);
        double z   = plugin.getConfig().getDouble("wealthy-exit-z",   -463.5);
        float yaw   = (float) plugin.getConfig().getDouble("wealthy-exit-yaw",   270.0);
        float pitch = (float) plugin.getConfig().getDouble("wealthy-exit-pitch",   0.0);

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            target.sendMessage(ChatColor.RED + "ワールドが見つかりません。管理者に連絡してください。");
            return true;
        }

        Location dest = new Location(world, x, y, z, yaw, pitch);
        target.teleport(dest);
        target.sendMessage(ChatColor.GRAY + "← 豪商エリアを退出しました。");
        target.playSound(dest, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.7f, 1.0f);

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
}
