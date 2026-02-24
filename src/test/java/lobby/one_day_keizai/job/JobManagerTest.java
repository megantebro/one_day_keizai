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

    @Test
    void isFarmer_trueForFarmer() {
        when(dataManager.getJob(playerId)).thenReturn(Job.FARMER);
        assertTrue(jobManager.isFarmer(playerId));
    }

    @Test
    void isFarmer_falseForOthers() {
        when(dataManager.getJob(playerId)).thenReturn(Job.BLACKSMITH);
        assertFalse(jobManager.isFarmer(playerId));
    }

    @Test
    void isBlacksmith_trueForBlacksmith() {
        when(dataManager.getJob(playerId)).thenReturn(Job.BLACKSMITH);
        assertTrue(jobManager.isBlacksmith(playerId));
    }

    @Test
    void isMerchant_trueForMerchant() {
        when(dataManager.getJob(playerId)).thenReturn(Job.MERCHANT);
        assertTrue(jobManager.isMerchant(playerId));
    }

    @Test
    void isMerchant_falseForNone() {
        when(dataManager.getJob(playerId)).thenReturn(Job.NONE);
        assertFalse(jobManager.isMerchant(playerId));
    }
}
