package lobby.one_day_keizai.command;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final JobManager jobManager;

    public JobCommand(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ使用可能です。");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "select" -> handleSelect(player, args);
            case "info"   -> handleInfo(player);
            case "list"   -> handleList(player);
            default       -> sendUsage(player);
        }

        return true;
    }

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /job select <farmer|blacksmith|merchant>");
            return;
        }

        Job job = Job.fromString(args[1]);
        if (job == null || job == Job.NONE) {
            player.sendMessage(ChatColor.RED + "職業名が正しくありません。farmer / blacksmith / merchant のいずれかを指定してください。");
            return;
        }

        Job current = jobManager.getJob(player.getUniqueId());
        if (current == job) {
            player.sendMessage(ChatColor.YELLOW + "すでに " + job.getColorCode() + job.getDisplayName() + ChatColor.YELLOW + " です。");
            return;
        }

        jobManager.setJob(player.getUniqueId(), job);

        player.sendMessage(ChatColor.GREEN + "職業を " + job.getColorCode() + job.getDisplayName() + ChatColor.GREEN + " に変更しました。");
        sendJobDescription(player, job);
    }

    private void handleInfo(Player player) {
        Job job = jobManager.getJob(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "=== あなたの職業 ===");
        player.sendMessage(ChatColor.YELLOW + "職業: " + job.getColorCode() + job.getDisplayName());
        sendJobDescription(player, job);
    }

    private void handleList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 職業一覧 ===");
        for (Job job : Job.values()) {
            if (job == Job.NONE) continue;
            player.sendMessage(job.getColorCode() + "【" + job.getDisplayName() + "】" + ChatColor.WHITE + " - " + getJobSummary(job));
        }
        player.sendMessage(ChatColor.YELLOW + "/job select <職業名> で職業を選択できます。");
    }

    private void sendJobDescription(Player player, Job job) {
        switch (job) {
            case FARMER -> {
                player.sendMessage(ChatColor.GREEN + "  農家の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 安全ワールドで農作物を栽培できます");
                player.sendMessage(ChatColor.WHITE + "  - 農家専用ショップで作物を売却できます（準備中）");
            }
            case BLACKSMITH -> {
                player.sendMessage(ChatColor.GOLD + "  鍛冶屋の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 鉄・金・ダイヤ・ネザライトのツール/装備をクラフトできます");
                player.sendMessage(ChatColor.WHITE + "  - 鍛冶屋専用ショップで装備を売却できます（準備中）");
            }
            case MERCHANT -> {
                player.sendMessage(ChatColor.AQUA + "  商人の特権:");
                player.sendMessage(ChatColor.WHITE + "  - 危険ワールドの遠方ショップにアクセスできます（準備中）");
                player.sendMessage(ChatColor.WHITE + "  - 安全↔危険ワールド間の差額で利益を出せます");
            }
            case NONE -> player.sendMessage(ChatColor.GRAY + "  無職: 石ツール以下のクラフトのみ可能です。");
        }
    }

    private String getJobSummary(Job job) {
        return switch (job) {
            case FARMER     -> "安全ワールドで農作物を栽培できる";
            case BLACKSMITH -> "鉄以上のツール・装備をクラフトできる";
            case MERCHANT   -> "危険ワールドの遠方ショップにアクセスできる";
            default         -> "";
        };
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /job select <farmer|blacksmith|merchant>  - 職業を選択");
        player.sendMessage(ChatColor.YELLOW + "  /job info                                  - 現在の職業を確認");
        player.sendMessage(ChatColor.YELLOW + "  /job list                                  - 職業一覧を表示");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> cmds = Arrays.asList("select", "info", "list");
            List<String> result = new ArrayList<>(cmds);
            result.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            List<String> jobs = Arrays.asList("farmer", "blacksmith", "merchant");
            List<String> result = new ArrayList<>(jobs);
            result.removeIf(s -> !s.startsWith(args[1].toLowerCase()));
            return result;
        }
        return new ArrayList<>();
    }
}
