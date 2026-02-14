package lobby.one_day_keizai.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class NametagManager {

    private static final String TEAM_CRIMINAL = "criminal";
    private static final String TEAM_PROTECTED = "protected";
    private static final String TEAM_NORMAL = "normal";

    private final Scoreboard scoreboard;

    public NametagManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        setupTeams();
    }

    private void setupTeams() {
        createOrGetTeam(TEAM_CRIMINAL, ChatColor.RED);
        createOrGetTeam(TEAM_PROTECTED, ChatColor.GREEN);
        createOrGetTeam(TEAM_NORMAL, ChatColor.WHITE);
    }

    private Team createOrGetTeam(String name, ChatColor color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.setColor(color);
        team.setPrefix(color.toString());
        return team;
    }

    public void setNormal(Player player) {
        removeFromAllTeams(player);
        Team team = scoreboard.getTeam(TEAM_NORMAL);
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    public void setCriminal(Player player) {
        removeFromAllTeams(player);
        Team team = scoreboard.getTeam(TEAM_CRIMINAL);
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    public void setProtected(Player player) {
        removeFromAllTeams(player);
        Team team = scoreboard.getTeam(TEAM_PROTECTED);
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    private void removeFromAllTeams(Player player) {
        String name = player.getName();
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
        }
    }

    /**
     * プレイヤーの状態に応じて適切なチームに設定する。
     */
    public void updateNametag(Player player, boolean isCriminal, boolean isProtected) {
        if (isProtected) {
            setProtected(player);
        } else if (isCriminal) {
            setCriminal(player);
        } else {
            setNormal(player);
        }
    }
}
