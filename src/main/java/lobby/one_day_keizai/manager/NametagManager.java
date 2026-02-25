package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーのネームタグを管理する。
 *
 * 表示形式: [職業名] プレイヤー名
 *   - 職業名: 各ジョブの色で表示（無職なら省略）
 *   - プレイヤー名: ステータス色で表示
 *       通常=白, リスポーン保護=緑, 指名手配=金
 *
 * 実装: プレイヤー1人につき1チーム（"nt_<playerName>"）を使用し、
 * prefix に職業名を設定してスコアボードAPI経由で他クライアントに配信。
 */
public class NametagManager {

    private static final ChatColor COLOR_WANTED   = ChatColor.GOLD;
    private static final ChatColor COLOR_PROTECTED = ChatColor.GREEN;
    private static final ChatColor COLOR_NORMAL    = ChatColor.WHITE;

    /** ステータス識別子 */
    private enum Status { NORMAL, WANTED, PROTECTED }

    private final Scoreboard scoreboard;

    /** JobManager は初期化後に setJobManager() で注入 */
    private JobManager jobManager;

    /** 各プレイヤーの現在ステータス（ネームタグ再描画時に参照） */
    private final Map<UUID, Status> statusMap = new ConcurrentHashMap<>();

    public NametagManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /**
     * 循環依存を避けるため JobManager は後から注入する。
     * One_day_keizai.onEnable() で JobManager 生成後に呼ぶこと。
     */
    public void setJobManager(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    // ─── ステータス変更 API ──────────────────────────────────────────────────

    public void setNormal(Player player) {
        statusMap.put(player.getUniqueId(), Status.NORMAL);
        refresh(player);
    }

    public void setWanted(Player player) {
        statusMap.put(player.getUniqueId(), Status.WANTED);
        refresh(player);
    }

    public void clearWanted(Player player) {
        setNormal(player);
    }

    /** 後方互換: setCriminal → setWanted に委譲。 */
    public void setCriminal(Player player) {
        setWanted(player);
    }

    public void setProtected(Player player) {
        statusMap.put(player.getUniqueId(), Status.PROTECTED);
        refresh(player);
    }

    /**
     * ステータスを直接指定して更新する。
     * ProtectionManager / WantedManager からの呼び出し用。
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

    /**
     * 職業が変わった時にネームタグを再描画する。
     * JobCommand / JobSelectionListener から呼び出す。
     */
    public void refreshJob(Player player) {
        refresh(player);
    }

    // ─── 内部処理 ────────────────────────────────────────────────────────────

    /**
     * 現在の (status, job) からチームを更新して全クライアントに配信する。
     */
    private void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        Status status = statusMap.getOrDefault(uuid, Status.NORMAL);
        Job job = jobManager != null ? jobManager.getJob(uuid) : Job.NONE;

        ChatColor nameColor = switch (status) {
            case WANTED    -> COLOR_WANTED;
            case PROTECTED -> COLOR_PROTECTED;
            default        -> COLOR_NORMAL;
        };

        String prefix = buildPrefix(job);

        Team team = getOrCreatePlayerTeam(player);
        team.setColor(nameColor);
        team.setPrefix(prefix);
    }

    /**
     * 職業プレフィックスを構築する。
     * 無職の場合は空文字（プレフィックスなし）。
     * 例: §a[農家] §r
     */
    private String buildPrefix(Job job) {
        if (job == null || job == Job.NONE) return "";
        return job.getColorCode() + "[" + job.getDisplayName() + "] ";
    }

    /**
     * プレイヤー専用チームを取得または作成する。
     * チーム名: "nt_" + playerName（最大 16+3=19 文字）
     */
    private Team getOrCreatePlayerTeam(Player player) {
        String teamName = "nt_" + player.getName();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        // プレイヤーがこのチームにいなければ追加（既存チームから除去）
        if (!team.hasEntry(player.getName())) {
            removeFromAllTeams(player);
            team.addEntry(player.getName());
        }
        return team;
    }

    private void removeFromAllTeams(Player player) {
        String name = player.getName();
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
        }
    }
}
