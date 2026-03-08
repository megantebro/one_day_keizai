package lobby.one_day_keizai.command;

import lobby.one_day_keizai.manager.WantedManager;
import lobby.one_day_keizai.manager.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
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
        if (args.length == 0) {
            if (sender instanceof Player p) sendUsage(p);
            else sender.sendMessage("使い方: /ow enter <player> | /ow return <player>");
            return true;
        }

        // ターゲットプレイヤーを解決
        // Playerから実行: 引数なしで自分自身 / コマンドブロック・コンソール: args[1]にプレイヤー名が必要
        Player player;
        if (sender instanceof Player p) {
            player = p;
        } else {
            // コマンドブロック or コンソール → プレイヤー名 or セレクターが必要
            if (args.length < 2) {
                sender.sendMessage("コマンドブロックからの使い方: /ow enter <player/@p> | /ow return <player/@p>");
                return true;
            }
            player = resolvePlayer(sender, args[1]);
            if (player == null) {
                sender.sendMessage("プレイヤーが見つかりません: " + args[1]);
                return true;
            }
        }

        switch (args[0].toLowerCase()) {
            case "enter" -> {
                // コマンドブロックからの実行は感圧板を踏んでいる場合のみ許可
                // 感圧板は薄いブロックのためプレイヤーのY座標と同じブロック座標になる
                if (!(sender instanceof Player)) {
                    org.bukkit.block.Block feet = player.getLocation().getBlock();
                    if (!feet.getType().name().contains("PRESSURE_PLATE")) return true;
                }
                worldManager.enterOverworld(player);
            }
            case "return" -> {
                // 指名手配中は帰還禁止
                if (wantedManager.isWanted(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "指名手配中は安全ワールドに戻れません！");
                    player.sendMessage(ChatColor.GOLD + "指名手配が解除されるまで待ってください。");
                    return true;
                }
                // 安全ワールド内（ショップ等）→ スポーン地点へ直接TP
                if (worldManager.isSafeWorld(player.getWorld())) {
                    org.bukkit.World safeWorld = Bukkit.getWorld(worldManager.getSafeWorldName());
                    if (safeWorld != null) {
                        player.teleport(safeWorld.getSpawnLocation());
                        player.sendMessage(ChatColor.GREEN + "スポーン地点に戻りました。");
                    }
                } else {
                    worldManager.returnToSafeWorld(player);
                }
            }
            default -> {
                if (sender instanceof Player p) sendUsage(p);
                else sender.sendMessage("使い方: /ow enter <player> | /ow return <player>");
            }
        }

        return true;
    }

    /** @p などのセレクターまたはプレイヤー名を解決して Player を返す */
    private Player resolvePlayer(CommandSender sender, String nameOrSelector) {
        if (nameOrSelector.startsWith("@")) {
            try {
                for (Entity e : Bukkit.selectEntities(sender, nameOrSelector)) {
                    if (e instanceof Player p) return p;
                }
                return null;
            } catch (IllegalArgumentException ignored) {}
        }
        return Bukkit.getPlayer(nameOrSelector);
    }

    private void sendUsage(Player player) {
        double fee = worldManager.getEntryFee(player);
        player.sendMessage(ChatColor.YELLOW + "=== オーバーワールド ===");
        player.sendMessage(ChatColor.GOLD + "/ow enter" + ChatColor.WHITE + " - オーバーワールドへ入場（入場料: " +
                ChatColor.GOLD + String.format("$%.0f", fee) +
                ChatColor.WHITE + " ／所持金の10%・上限$20,000）");
        player.sendMessage(ChatColor.GOLD + "/ow return" + ChatColor.WHITE + " - 安全ワールドへ帰還（全額返金）");
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
