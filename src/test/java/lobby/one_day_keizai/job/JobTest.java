package lobby.one_day_keizai.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    @Test
    void fromString_enumName_returnsJob() {
        assertEquals(Job.FARMER, Job.fromString("FARMER"));
        assertEquals(Job.BLACKSMITH, Job.fromString("BLACKSMITH"));
        assertEquals(Job.MERCHANT, Job.fromString("MERCHANT"));
        assertEquals(Job.NONE, Job.fromString("NONE"));
    }

    @Test
    void fromString_caseInsensitive() {
        assertEquals(Job.FARMER, Job.fromString("farmer"));
        assertEquals(Job.BLACKSMITH, Job.fromString("blacksmith"));
        assertEquals(Job.MERCHANT, Job.fromString("merchant"));
    }

    @Test
    void fromString_displayName_returnsJob() {
        assertEquals(Job.FARMER, Job.fromString("農家"));
        assertEquals(Job.BLACKSMITH, Job.fromString("鍛冶屋"));
        assertEquals(Job.MERCHANT, Job.fromString("商人"));
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
    }
}
