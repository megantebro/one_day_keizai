package lobby.one_day_keizai.manager;

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
class CriminalManagerTest {

    @Mock
    private PlayerDataManager dataManager;

    private CriminalManager criminalManager;

    private final UUID playerA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        criminalManager = new CriminalManager(dataManager, 3);
    }

    @Test
    void isCriminal_delegatesToDataManager() {
        when(dataManager.isCriminal(playerA)).thenReturn(true);
        assertTrue(criminalManager.isCriminal(playerA));
        verify(dataManager).isCriminal(playerA);
    }

    @Test
    void isCriminal_returnsFalseByDefault() {
        when(dataManager.isCriminal(playerA)).thenReturn(false);
        assertFalse(criminalManager.isCriminal(playerA));
    }

    @Test
    void setCriminal_delegatesToDataManager() {
        criminalManager.setCriminal(playerA, true);
        verify(dataManager).setCriminal(playerA, true);
    }

    @Test
    void getInnocentKillCount_delegatesToDataManager() {
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(2);
        assertEquals(2, criminalManager.getInnocentKillCount(playerA));
    }

    @Test
    void incrementInnocentKill_firstKill_doesNotBecomeCriminal() {
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(0);

        boolean becameCriminal = criminalManager.incrementInnocentKill(playerA);

        assertFalse(becameCriminal);
        verify(dataManager).setInnocentKillCount(playerA, 1);
        verify(dataManager, never()).setCriminal(eq(playerA), eq(true));
    }

    @Test
    void incrementInnocentKill_secondKill_doesNotBecomeCriminal() {
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(1);

        boolean becameCriminal = criminalManager.incrementInnocentKill(playerA);

        assertFalse(becameCriminal);
        verify(dataManager).setInnocentKillCount(playerA, 2);
        verify(dataManager, never()).setCriminal(eq(playerA), eq(true));
    }

    @Test
    void incrementInnocentKill_thirdKill_becomesCriminal() {
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(2);

        boolean becameCriminal = criminalManager.incrementInnocentKill(playerA);

        assertTrue(becameCriminal);
        verify(dataManager).setInnocentKillCount(playerA, 3);
        verify(dataManager).setCriminal(playerA, true);
    }

    @Test
    void incrementInnocentKill_fourthKill_stillCriminal() {
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(3);

        boolean becameCriminal = criminalManager.incrementInnocentKill(playerA);

        assertTrue(becameCriminal);
        verify(dataManager).setInnocentKillCount(playerA, 4);
        verify(dataManager).setCriminal(playerA, true);
    }

    @Test
    void incrementInnocentKill_customLimit_respectsLimit() {
        CriminalManager customManager = new CriminalManager(dataManager, 5);
        when(dataManager.getInnocentKillCount(playerA)).thenReturn(3);

        boolean becameCriminal = customManager.incrementInnocentKill(playerA);

        assertFalse(becameCriminal);
        verify(dataManager).setInnocentKillCount(playerA, 4);
    }
}
