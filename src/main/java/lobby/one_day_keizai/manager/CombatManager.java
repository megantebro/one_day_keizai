package lobby.one_day_keizai.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    // 「誰が先に殴ったか」を被害者視点で記録: victim -> firstAttacker
    private final Map<UUID, UUID> firstAttacker = new HashMap<>();

    // 最後にダメージを受けた時刻
    private final Map<UUID, Long> lastDamagedTime = new HashMap<>();

    // 最後に攻撃してきた相手
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    private final int combatLogoutSeconds;

    public CombatManager(int combatLogoutSeconds) {
        this.combatLogoutSeconds = combatLogoutSeconds;
    }

    /**
     * AがBを攻撃した時に呼ぶ。
     * まだB視点で先制攻撃者が記録されていなければAを記録する。
     */
    public void recordAttack(UUID attacker, UUID victim) {
        // 先制攻撃記録（まだ記録がなければ）
        if (!firstAttacker.containsKey(victim)) {
            // 反撃チェック: attackerが既にvictimから先に殴られている場合は記録しない
            UUID attackersFirst = firstAttacker.get(attacker);
            if (attackersFirst == null || !attackersFirst.equals(victim)) {
                firstAttacker.put(victim, attacker);
            }
        }

        // 最後の攻撃者と被ダメ時刻を記録
        lastAttacker.put(victim, attacker);
        lastDamagedTime.put(victim, System.currentTimeMillis());
    }

    /**
     * 正当防衛判定：killerがvictimを殺した時、killerが先に殴った側かどうか。
     * @return killerが先に殴った側（=無実キル）ならtrue
     */
    public boolean isInnocentKill(UUID killer, UUID victim) {
        // victim視点でfirstAttackerがkillerなら、killerが先に殴った → killerの無実キル
        UUID first = firstAttacker.get(victim);
        if (first != null && first.equals(killer)) {
            return true;
        }
        // killer視点でfirstAttackerがvictimなら、victimが先に殴った → 正当防衛
        UUID firstOnKiller = firstAttacker.get(killer);
        if (firstOnKiller != null && firstOnKiller.equals(victim)) {
            return false;
        }
        // 先制攻撃記録がない場合はkillerの無実キルとみなす
        return true;
    }

    /**
     * 死亡・戦闘終了時に先制攻撃記録をクリアする。
     */
    public void clearCombatData(UUID player1, UUID player2) {
        firstAttacker.remove(player1);
        firstAttacker.remove(player2);
        lastAttacker.remove(player1);
        lastAttacker.remove(player2);
        lastDamagedTime.remove(player1);
        lastDamagedTime.remove(player2);
    }

    /**
     * 戦闘ログアウト判定：最後に被ダメしてからcombatLogoutSeconds以内か。
     */
    public boolean isInCombat(UUID player) {
        Long lastDamaged = lastDamagedTime.get(player);
        if (lastDamaged == null) return false;
        return (System.currentTimeMillis() - lastDamaged) < (combatLogoutSeconds * 1000L);
    }

    /**
     * 最後にこのプレイヤーを攻撃した人を返す。
     */
    public UUID getLastAttacker(UUID player) {
        return lastAttacker.get(player);
    }

    /**
     * プレイヤーのすべての戦闘データをクリアする。
     */
    public void clearAllData(UUID player) {
        firstAttacker.remove(player);
        lastAttacker.remove(player);
        lastDamagedTime.remove(player);
    }
}
