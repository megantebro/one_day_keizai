package lobby.one_day_keizai.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatManager {

    // 先制攻撃をペア単位で記録: "attackerUUID|victimUUID" 形式
    // 例: "A|B" → AがBに対して先制攻撃した（B→Aの記録がない状態で）
    private final Set<String> firstHits = new HashSet<>();

    // 最後にダメージを受けた時刻
    private final Map<UUID, Long> lastDamagedTime = new HashMap<>();

    // 最後に攻撃してきた相手
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    private final int combatLogoutSeconds;

    public CombatManager(int combatLogoutSeconds) {
        this.combatLogoutSeconds = combatLogoutSeconds;
    }

    /**
     * ペアキーを生成する。
     */
    private String pairKey(UUID attacker, UUID victim) {
        return attacker + "|" + victim;
    }

    /**
     * AがBを攻撃した時に呼ぶ。
     * A→B のペアで「Aが先制」と記録する（ただしB→Aが既に記録されている場合は記録しない）。
     * これにより第三者Cが先にBを殴っても A→B ペアは独立して追跡される。
     */
    public void recordAttack(UUID attacker, UUID victim) {
        // A→B ペアがまだ未登録 かつ B→A も未登録の場合のみ A を先制として記録
        if (!firstHits.contains(pairKey(attacker, victim))
                && !firstHits.contains(pairKey(victim, attacker))) {
            firstHits.add(pairKey(attacker, victim));
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
        // victim→killer の先制記録がある → victimが先に殴った → killer は正当防衛
        if (firstHits.contains(pairKey(victim, killer))) return false;
        // killer→victim の先制記録がある → killer が先に殴った → 無実キル
        if (firstHits.contains(pairKey(killer, victim))) return true;
        // 記録なし → デフォルトで無実キルとみなす
        return true;
    }

    /**
     * 死亡・戦闘終了時に先制攻撃記録をクリアする（2者間のペアのみ）。
     */
    public void clearCombatData(UUID player1, UUID player2) {
        firstHits.remove(pairKey(player1, player2));
        firstHits.remove(pairKey(player2, player1));
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
     * プレイヤーのすべての戦闘データをクリアする（全ペア）。
     */
    public void clearAllData(UUID player) {
        String p = player.toString();
        firstHits.removeIf(key -> {
            int sep = key.indexOf('|');
            return sep >= 0 && (key.substring(0, sep).equals(p) || key.substring(sep + 1).equals(p));
        });
        lastAttacker.remove(player);
        lastDamagedTime.remove(player);
    }
}
