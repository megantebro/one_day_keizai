package lobby.one_day_keizai.job;

import lobby.one_day_keizai.data.PlayerDataManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobManagerTest {

    @Mock private PlayerDataManager dataManager;
    private JobManager jobManager;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobManager = new JobManager(dataManager);
    }

    // ─── getJob / setJob ─────────────────────────────────────────────────────

    @Test
    void getJob_returnsValueFromDataManager() {
        when(dataManager.getJob(playerId)).thenReturn(Job.FARMER);
        assertEquals(Job.FARMER, jobManager.getJob(playerId));
    }

    @Test
    void setJob_savesToDataManagerAndSaves() {
        jobManager.setJob(playerId, Job.BLACKSMITH);
        verify(dataManager).setJob(playerId, Job.BLACKSMITH);
        verify(dataManager).save();
    }

    // ─── isFarmer (農家 + 資本家) ──────────────────────────────────────────

    @Test
    void isFarmer_trueForFarmer() {
        when(dataManager.getJob(playerId)).thenReturn(Job.FARMER);
        assertTrue(jobManager.isFarmer(playerId));
    }

    @Test
    void isFarmer_trueForCapitalist() {
        when(dataManager.getJob(playerId)).thenReturn(Job.CAPITALIST);
        assertTrue(jobManager.isFarmer(playerId));
    }

    @Test
    void isFarmer_falseForOthers() {
        when(dataManager.getJob(playerId)).thenReturn(Job.BLACKSMITH);
        assertFalse(jobManager.isFarmer(playerId));
    }

    @Test
    void isFarmer_falseForNone() {
        when(dataManager.getJob(playerId)).thenReturn(Job.NONE);
        assertFalse(jobManager.isFarmer(playerId));
    }

    // ─── isBlacksmith (鍛冶屋 + エンチャンター) ─────────────────────────────

    @Test
    void isBlacksmith_trueForBlacksmith() {
        when(dataManager.getJob(playerId)).thenReturn(Job.BLACKSMITH);
        assertTrue(jobManager.isBlacksmith(playerId));
    }

    @Test
    void isBlacksmith_trueForEnchanter() {
        when(dataManager.getJob(playerId)).thenReturn(Job.ENCHANTER);
        assertTrue(jobManager.isBlacksmith(playerId));
    }

    @Test
    void isBlacksmith_falseForFarmer() {
        when(dataManager.getJob(playerId)).thenReturn(Job.FARMER);
        assertFalse(jobManager.isBlacksmith(playerId));
    }

    // ─── isMerchant (商人 + 豪商) ────────────────────────────────────────────

    @Test
    void isMerchant_trueForMerchant() {
        when(dataManager.getJob(playerId)).thenReturn(Job.MERCHANT);
        assertTrue(jobManager.isMerchant(playerId));
    }

    @Test
    void isMerchant_trueForWealthyMerchant() {
        when(dataManager.getJob(playerId)).thenReturn(Job.WEALTHY_MERCHANT);
        assertTrue(jobManager.isMerchant(playerId));
    }

    @Test
    void isMerchant_falseForNone() {
        when(dataManager.getJob(playerId)).thenReturn(Job.NONE);
        assertFalse(jobManager.isMerchant(playerId));
    }

    // ─── 上級職専用メソッド ──────────────────────────────────────────────────

    @Test
    void isCapitalist_trueForCapitalist() {
        when(dataManager.getJob(playerId)).thenReturn(Job.CAPITALIST);
        assertTrue(jobManager.isCapitalist(playerId));
    }

    @Test
    void isCapitalist_falseForFarmer() {
        when(dataManager.getJob(playerId)).thenReturn(Job.FARMER);
        assertFalse(jobManager.isCapitalist(playerId));
    }

    @Test
    void isEnchanter_trueForEnchanter() {
        when(dataManager.getJob(playerId)).thenReturn(Job.ENCHANTER);
        assertTrue(jobManager.isEnchanter(playerId));
    }

    @Test
    void isEnchanter_falseForBlacksmith() {
        when(dataManager.getJob(playerId)).thenReturn(Job.BLACKSMITH);
        assertFalse(jobManager.isEnchanter(playerId));
    }

    @Test
    void isWealthyMerchant_trueForWealthyMerchant() {
        when(dataManager.getJob(playerId)).thenReturn(Job.WEALTHY_MERCHANT);
        assertTrue(jobManager.isWealthyMerchant(playerId));
    }

    @Test
    void isWealthyMerchant_falseForMerchant() {
        when(dataManager.getJob(playerId)).thenReturn(Job.MERCHANT);
        assertFalse(jobManager.isWealthyMerchant(playerId));
    }
}
