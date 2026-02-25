package lobby.one_day_keizai.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    // ─── fromString ───────────────────────────────────────────────────────────

    @Test
    void fromString_enumName_returnsJob() {
        assertEquals(Job.FARMER, Job.fromString("FARMER"));
        assertEquals(Job.BLACKSMITH, Job.fromString("BLACKSMITH"));
        assertEquals(Job.MERCHANT, Job.fromString("MERCHANT"));
        assertEquals(Job.NONE, Job.fromString("NONE"));
        // 上級職
        assertEquals(Job.CAPITALIST, Job.fromString("CAPITALIST"));
        assertEquals(Job.ENCHANTER, Job.fromString("ENCHANTER"));
        assertEquals(Job.WEALTHY_MERCHANT, Job.fromString("WEALTHY_MERCHANT"));
    }

    @Test
    void fromString_caseInsensitive() {
        assertEquals(Job.FARMER, Job.fromString("farmer"));
        assertEquals(Job.BLACKSMITH, Job.fromString("blacksmith"));
        assertEquals(Job.MERCHANT, Job.fromString("merchant"));
        assertEquals(Job.CAPITALIST, Job.fromString("capitalist"));
        assertEquals(Job.ENCHANTER, Job.fromString("enchanter"));
        assertEquals(Job.WEALTHY_MERCHANT, Job.fromString("wealthy_merchant"));
    }

    @Test
    void fromString_displayName_returnsJob() {
        assertEquals(Job.FARMER, Job.fromString("農家"));
        assertEquals(Job.BLACKSMITH, Job.fromString("鍛冶屋"));
        assertEquals(Job.MERCHANT, Job.fromString("商人"));
        assertEquals(Job.CAPITALIST, Job.fromString("資本家"));
        assertEquals(Job.ENCHANTER, Job.fromString("エンチャンター"));
        assertEquals(Job.WEALTHY_MERCHANT, Job.fromString("豪商"));
    }

    @Test
    void fromString_unknown_returnsNull() {
        assertNull(Job.fromString("unknown"));
        assertNull(Job.fromString("warrior"));
    }

    @Test
    void fromString_null_returnsNone() {
        assertEquals(Job.NONE, Job.fromString(null));
    }

    @Test
    void getDisplayName_returnsJapaneseName() {
        assertEquals("農家", Job.FARMER.getDisplayName());
        assertEquals("鍛冶屋", Job.BLACKSMITH.getDisplayName());
        assertEquals("商人", Job.MERCHANT.getDisplayName());
        assertEquals("無職", Job.NONE.getDisplayName());
        assertEquals("資本家", Job.CAPITALIST.getDisplayName());
        assertEquals("エンチャンター", Job.ENCHANTER.getDisplayName());
        assertEquals("豪商", Job.WEALTHY_MERCHANT.getDisplayName());
    }

    // ─── isUpperTier ─────────────────────────────────────────────────────────

    @Test
    void isUpperTier_falseForBasicJobs() {
        assertFalse(Job.NONE.isUpperTier());
        assertFalse(Job.FARMER.isUpperTier());
        assertFalse(Job.BLACKSMITH.isUpperTier());
        assertFalse(Job.MERCHANT.isUpperTier());
    }

    @Test
    void isUpperTier_trueForUpperJobs() {
        assertTrue(Job.CAPITALIST.isUpperTier());
        assertTrue(Job.ENCHANTER.isUpperTier());
        assertTrue(Job.WEALTHY_MERCHANT.isUpperTier());
    }

    // ─── getBaseJob ──────────────────────────────────────────────────────────

    @Test
    void getBaseJob_upperJobReturnsBase() {
        assertEquals(Job.FARMER, Job.CAPITALIST.getBaseJob());
        assertEquals(Job.BLACKSMITH, Job.ENCHANTER.getBaseJob());
        assertEquals(Job.MERCHANT, Job.WEALTHY_MERCHANT.getBaseJob());
    }

    @Test
    void getBaseJob_basicJobReturnsSelf() {
        assertEquals(Job.FARMER, Job.FARMER.getBaseJob());
        assertEquals(Job.BLACKSMITH, Job.BLACKSMITH.getBaseJob());
        assertEquals(Job.MERCHANT, Job.MERCHANT.getBaseJob());
        assertEquals(Job.NONE, Job.NONE.getBaseJob());
    }

    // ─── getUpperJob ─────────────────────────────────────────────────────────

    @Test
    void getUpperJob_onlyBlacksmithCanPromote() {
        // 鍛冶屋のみエンチャンターへの昇格パスを持つ
        assertNull(Job.FARMER.getUpperJob());
        assertEquals(Job.ENCHANTER, Job.BLACKSMITH.getUpperJob());
        assertNull(Job.MERCHANT.getUpperJob());
    }

    @Test
    void getUpperJob_nonPromotableJobsReturnNull() {
        assertNull(Job.NONE.getUpperJob());
        assertNull(Job.FARMER.getUpperJob());
        assertNull(Job.MERCHANT.getUpperJob());
        assertNull(Job.CAPITALIST.getUpperJob());
        assertNull(Job.ENCHANTER.getUpperJob());
        assertNull(Job.WEALTHY_MERCHANT.getUpperJob());
    }
}
