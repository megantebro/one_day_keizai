package lobby.one_day_keizai.command;

import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.listener.JobFarmListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /farm autoplant — 自動植え直しスキルのON/OFFトグル（農家専用）
 */
public class FarmCommand implements CommandExecutor, TabCompleter {

    private final JobManager jobManager;
    private final JobFarmListener farmListener;

    public FarmCommand(JobManager jobManager, JobFarmListener farmListener) {
        this.jobManager   = jobManager;
        this.farmListener = farmListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ使用可能です。");
            return true;
        }

        if (!jobManager.isFarmer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "このコマンドは農家のみ使用可能です。");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("autoplant")) {
            boolean now = farmListener.toggleAutoReplant(player.getUniqueId());
            if (now) {
                player.sendMessage(ChatColor.GREEN + "✅ 自動植え直し: " + ChatColor.BOLD + "ON");
                player.sendMessage(ChatColor.GRAY + "完全成長した作物を収穫すると自動で植え直されます。");
            } else {
                player.sendMessage(ChatColor.YELLOW + "⛔ 自動植え直し: " + ChatColor.BOLD + "OFF");
            }
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "/farm autoplant — 自動植え直しON/OFF切り替え");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "autoplant".startsWith(args[0].toLowerCase())) {
            return List.of("autoplant");
        }
        return Collections.emptyList();
    }
}
