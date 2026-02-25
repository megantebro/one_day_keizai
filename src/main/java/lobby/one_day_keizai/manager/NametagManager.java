package lobby.one_day_keizai.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class NametagManager {

    private static final String TEAM_WANTED = "wanted";
    private static final String TEAM_PROTECTED = "protected";
    private static final String TEAM_NORMAL = "normal";

    private final Scoreboard scoreboard;

    public NametagManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        setupTeams();
    }

    private void setupTeams() {
        createOrGetTeam(TEAM_WANTED, ChatColor.GOLD);
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

    /** 指名手配（金色ネームタグ）に設定する。 */
    public void setWanted(Player player) {
        removeFromAllTeams(player);
        Team team = scoreboard.getTeam(TEAM_WANTED);
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    /** 指名手配を解除し通常ネームタグに戻す。 */
    public void clearWanted(Player player) {
        setNormal(player);
    }

    /** 後方互換: setCriminal → setWanted に委譲。 */
    public void setCriminal(Player player) {
        setWanted(player);
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
     * @param isWanted    指名手配中か
     * @param isProtected リスポーン保護中か
     */
    public void updateNametag(Player player, boolean isWanted, boolean isProtected) {
        if (isProtected) {
            setProtected(player);
        } else if (isWanted) {
            setWanted(player);
        } else {
            setNormal(player);
        }
    }
}
