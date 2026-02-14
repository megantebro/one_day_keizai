package lobby.one_day_keizai.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CombatManagerTest {

    private CombatManager combatManager;

    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();
    private final UUID playerC = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        combatManager = new CombatManager(30);
    }

    // --- 正当防衛判定テスト ---

    @Test
    void isInnocentKill_attackerKillsVictim_isInnocentKill() {
        // AがBを先に殴った → AがBを殺した → Aの無実キル
        combatManager.recordAttack(playerA, playerB);
        assertTrue(combatManager.isInnocentKill(playerA, playerB));
    }

    @Test
    void isInnocentKill_victimKillsAttacker_isSelfDefense() {
        // AがBを先に殴った → BがAを殺した → 正当防衛（無実キルではない）
        combatManager.recordAttack(playerA, playerB);
        assertFalse(combatManager.isInnocentKill(playerB, playerA));
    }

    @Test
    void isInnocentKill_mutualCombat_firstAttackerIsInnocent() {
        // AがBを先に殴った、その後BもAを殴った
        combatManager.recordAttack(playerA, playerB);
        combatManager.recordAttack(playerB, playerA);

        // AがBを殺す → Aが先に殴ったのでAの無実キル
        assertTrue(combatManager.isInnocentKill(playerA, playerB));
        // BがAを殺す → Aが先に殴ったのでBは正当防衛
        assertFalse(combatManager.isInnocentKill(playerB, playerA));
    }

    @Test
    void isInnocentKill_noRecords_treatedAsInnocentKill() {
        // 記録なしの場合は無実キルとみなす
        assertTrue(combatManager.isInnocentKill(playerA, playerB));
    }

    @Test
    void isInnocentKill_thirdPartyKill_isInnocentKill() {
        // AがBを殴った後、CがBを殺す → Cの無実キル
        combatManager.recordAttack(playerA, playerB);
        assertTrue(combatManager.isInnocentKill(playerC, playerB));
    }

    // --- 先制攻撃記録テスト ---

    @Test
    void recordAttack_firstAttackRecorded_subsequentIgnored() {
        combatManager.recordAttack(playerA, playerB);
        combatManager.recordAttack(playerC, playerB); // 2番目の攻撃者は先制として記録されない

        // Aが先に殴ったまま
        assertTrue(combatManager.isInnocentKill(playerA, playerB));
    }

    // --- 最後の攻撃者テスト ---

    @Test
    void getLastAttacker_returnsLastAttacker() {
        combatManager.recordAttack(playerA, playerB);
        assertEquals(playerA, combatManager.getLastAttacker(playerB));

        combatManager.recordAttack(playerC, playerB);
        assertEquals(playerC, combatManager.getLastAttacker(playerB));
    }

    @Test
    void getLastAttacker_noAttack_returnsNull() {
        assertNull(combatManager.getLastAttacker(playerA));
    }

    // --- 戦闘ログアウト判定テスト ---

    @Test
    void isInCombat_justAttacked_returnsTrue() {
        combatManager.recordAttack(playerA, playerB);
        // playerBは直後なので戦闘中
        assertTrue(combatManager.isInCombat(playerB));
    }

    @Test
    void isInCombat_noDamage_returnsFalse() {
        assertFalse(combatManager.isInCombat(playerA));
    }

    @Test
    void isInCombat_attackerNotInCombat() {
        combatManager.recordAttack(playerA, playerB);
        // 攻撃者Aはダメージを受けていないので戦闘中ではない
        assertFalse(combatManager.isInCombat(playerA));
    }

    // --- データクリアテスト ---

    @Test
    void clearCombatData_removesAllDataForBothPlayers() {
        combatManager.recordAttack(playerA, playerB);

        combatManager.clearCombatData(playerA, playerB);

        assertNull(combatManager.getLastAttacker(playerA));
        assertNull(combatManager.getLastAttacker(playerB));
        // firstAttackerもクリアされているのでデフォルトの無実キル判定になる
        assertTrue(combatManager.isInnocentKill(playerA, playerB));
    }

    @Test
    void clearAllData_removesAllDataForPlayer() {
        combatManager.recordAttack(playerA, playerB);

        combatManager.clearAllData(playerB);

        assertNull(combatManager.getLastAttacker(playerB));
        assertFalse(combatManager.isInCombat(playerB));
    }

    // --- エッジケース ---

    @Test
    void recordAttack_selfAttack_handled() {
        // 自分自身への攻撃（通常起きないが安全性確認）
        combatManager.recordAttack(playerA, playerA);
        assertEquals(playerA, combatManager.getLastAttacker(playerA));
    }

    @Test
    void multipleIndependentFights() {
        // A vs B と C vs D の独立した戦闘
        UUID playerD = UUID.randomUUID();

        combatManager.recordAttack(playerA, playerB);
        combatManager.recordAttack(playerC, playerD);

        assertTrue(combatManager.isInnocentKill(playerA, playerB));
        assertTrue(combatManager.isInnocentKill(playerC, playerD));
        assertFalse(combatManager.isInnocentKill(playerB, playerA));
        assertFalse(combatManager.isInnocentKill(playerD, playerC));
    }
}
