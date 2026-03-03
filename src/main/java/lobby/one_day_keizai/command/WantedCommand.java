package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.WantedManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /wanted set <player> [bounty] — 指定プレイヤーを賞金首にする（OP専用）
 * /wanted clear <player>       — 指名手配を解除する（OP専用）
 * /wanted list                 — 現在の賞金首一覧
 */
public class WantedCommand implements CommandExecutor, TabCompleter {

    private final WantedManager wantedManager;

    public WantedCommand(WantedManager wantedManager) {
        this.wantedManager = wantedManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(ChatColor.RED + "このコマンドはOPのみ使用可能です。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "使い方: /wanted set <player> [bounty]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[1]);
                    return true;
                }
                double bounty = 5000.0;
                if (args.length >= 3) {
                    try {
                        bounty = Double.parseDouble(args[2]);
                        if (bounty <= 0) {
                            sender.sendMessage(ChatColor.RED + "懸賞金は1以上の値を指定してください。");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "懸賞金は数値で指定してください。");
                        return true;
                    }
                }
                wantedManager.makeWanted(target, bounty);
                sender.sendMessage(ChatColor.GOLD + target.getName() + " を賞金首にしました（懸賞金: $" + String.format("%.0f", bounty) + "）");
            }
            case "clear" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(ChatColor.RED + "このコマンドはOPのみ使用可能です。");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "使い方: /wanted clear <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "プレイヤーが見つかりません: " + args[1]);
                    return true;
                }
                wantedManager.clearWanted(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + target.getName() + " の指名手配を解除しました。");
            }
            case "list" -> {
                List<String> wanted = wantedManager.getWantedUUIDs().stream()
                        .map(uuid -> {
                            Player p = Bukkit.getPlayer(uuid);
                            String name = p != null ? p.getName() : uuid.toString();
                            double bounty = wantedManager.getBounty(uuid);
                            return name + " ($" + String.format("%.0f", bounty) + ")";
                        })
                        .collect(Collectors.toList());
                if (wanted.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "現在の賞金首: なし");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== 現在の賞金首 ===");
                    wanted.forEach(s -> sender.sendMessage(ChatColor.RED + "  ⚠ " + s));
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/wanted set <player> [bounty] — 賞金首にする（OP）");
        sender.sendMessage(ChatColor.YELLOW + "/wanted clear <player>        — 解除（OP）");
        sender.sendMessage(ChatColor.YELLOW + "/wanted list                  — 一覧");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "clear", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
