package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.WantedManager;
import lobby.one_day_keizai.manager.WorldManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class OverworldCommand implements CommandExecutor, TabCompleter {

    private final WorldManager worldManager;
    private final WantedManager wantedManager;

    public OverworldCommand(WorldManager worldManager, WantedManager wantedManager) {
        this.worldManager = worldManager;
        this.wantedManager = wantedManager;
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
            case "enter" -> worldManager.enterOverworld(player);
            case "return" -> {
                // 指名手配中は帰還禁止
                if (wantedManager.isWanted(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "指名手配中は安全ワールドに戻れません！");
                    player.sendMessage(ChatColor.GOLD + "指名手配が解除されるまで待ってください。");
                    return true;
                }
                worldManager.returnToSafeWorld(player);
            }
            default -> sendUsage(player);
        }

        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== オーバーワールド ===");
        player.sendMessage(ChatColor.GOLD + "/ow enter" + ChatColor.WHITE + " - オーバーワールドへ入場（入場料: " +
                ChatColor.GOLD + String.format("$%.0f", worldManager.getEntryFee()) + ChatColor.WHITE + "）");
        player.sendMessage(ChatColor.GOLD + "/ow return" + ChatColor.WHITE + " - 安全ワールドへ帰還（返金あり）");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("enter".startsWith(input)) completions.add("enter");
            if ("return".startsWith(input)) completions.add("return");
        }
        return completions;
    }
}
