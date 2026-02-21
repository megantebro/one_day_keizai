package lobby.one_day_keizai.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

public class BalanceScoreboardManager {

    private final JavaPlugin plugin;
    private final Economy economy;
    private final Scoreboard scoreboard;
    private Objective objective;
    private final Set<String> currentEntries = new HashSet<>();

    public BalanceScoreboardManager(JavaPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        setupObjective();
    }

    private void setupObjective() {
        Objective existing = scoreboard.getObjective("balancetop");
        if (existing != null) {
            existing.unregister();
        }
        objective = scoreboard.registerNewObjective(
                "balancetop", Criteria.DUMMY, ChatColor.GOLD + "== Balance Top ==");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void startUpdater() {
        // 5秒ごとに更新（100tick）
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboard, 0L, 100L);
    }

    private void updateScoreboard() {
        // 古いエントリをクリア
        for (String entry : currentEntries) {
            scoreboard.resetScores(entry);
        }
        currentEntries.clear();

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        // 全オンラインプレイヤーの残高を取得してソート、上位5名を取得
        List<Map.Entry<String, Integer>> top5 = onlinePlayers.stream()
                .map(p -> Map.entry(p.getName(), (int) economy.getBalance(p)))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, Integer> entry = top5.get(i);
            String displayName = ChatColor.YELLOW + "" + (i + 1) + ". " +
                    ChatColor.WHITE + entry.getKey();
            objective.getScore(displayName).setScore(entry.getValue());
            currentEntries.add(displayName);
        }
    }
}
