package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.TestHelper;
import lobby.one_day_keizai.data.PlayerDataManager;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebtManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private PlayerDataManager dataManager;
    @Mock private CriminalManager criminalManager;
    @Mock private NametagManager nametagManager;
    @Mock private Server server;

    private DebtManager debtManager;

    private final UUID creditorId = UUID.randomUUID();
    private final UUID debtorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TestHelper.setupBukkitServer(server);
        when(dataManager.loadDebts()).thenReturn(new ArrayList<>());
        debtManager = new DebtManager(plugin, dataManager, criminalManager, nametagManager);
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardownBukkitServer();
    }

    // --- addDebt ---

    @Test
    void addDebt_createsDebt() {
        long deadline = System.currentTimeMillis() + 60000;
        debtManager.addDebt(creditorId, debtorId, 1000, deadline);

        assertTrue(debtManager.isDebtor(debtorId));
        assertEquals(creditorId, debtManager.getCreditor(debtorId));
    }

    @Test
    void addDebt_savesToDataManager() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        verify(dataManager, atLeastOnce()).saveDebts(anyList());
        verify(dataManager, atLeastOnce()).save();
    }

    // --- isDebtor ---

    @Test
    void isDebtor_noDebt_returnsFalse() {
        assertFalse(debtManager.isDebtor(debtorId));
    }

    @Test
    void isDebtor_hasDebt_returnsTrue() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        assertTrue(debtManager.isDebtor(debtorId));
    }

    @Test
    void isDebtor_creditorIsNotDebtor() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        assertFalse(debtManager.isDebtor(creditorId));
    }

    // --- getCreditor ---

    @Test
    void getCreditor_noDebt_returnsNull() {
        assertNull(debtManager.getCreditor(debtorId));
    }

    @Test
    void getCreditor_hasDebt_returnsCreditor() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        assertEquals(creditorId, debtManager.getCreditor(debtorId));
    }

    // --- getDebtsForPlayer ---

    @Test
    void getDebtsForPlayer_noDebts_returnsEmpty() {
        assertTrue(debtManager.getDebtsForPlayer(creditorId).isEmpty());
    }

    @Test
    void getDebtsForPlayer_asCreditor_returnsDebts() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        List<DebtManager.Debt> debts = debtManager.getDebtsForPlayer(creditorId);
        assertEquals(1, debts.size());
        assertEquals(500, debts.get(0).amount);
    }

    @Test
    void getDebtsForPlayer_asDebtor_returnsDebts() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        List<DebtManager.Debt> debts = debtManager.getDebtsForPlayer(debtorId);
        assertEquals(1, debts.size());
    }

    // --- repay ---

    @Test
    void repay_fullRepayment_removesDebt() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        double repaid = debtManager.repay(debtorId, creditorId, 1000);
        assertEquals(1000, repaid);
        assertFalse(debtManager.isDebtor(debtorId));
    }

    @Test
    void repay_partialRepayment_reducesDebt() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        double repaid = debtManager.repay(debtorId, creditorId, 400);
        assertEquals(400, repaid);
        assertTrue(debtManager.isDebtor(debtorId));
        List<DebtManager.Debt> debts = debtManager.getDebtsForPlayer(debtorId);
        assertEquals(600, debts.get(0).amount);
    }

    @Test
    void repay_overpayment_onlyRepaysDebtAmount() {
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        double repaid = debtManager.repay(debtorId, creditorId, 1000);
        assertEquals(500, repaid);
        assertFalse(debtManager.isDebtor(debtorId));
    }

    @Test
    void repay_noDebt_returnsZero() {
        double repaid = debtManager.repay(debtorId, creditorId, 1000);
        assertEquals(0, repaid);
    }

    @Test
    void repay_wrongCreditor_returnsZero() {
        UUID otherCreditor = UUID.randomUUID();
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        double repaid = debtManager.repay(debtorId, otherCreditor, 500);
        assertEquals(0, repaid);
        assertTrue(debtManager.isDebtor(debtorId));
    }

    // --- forgive ---

    @Test
    void forgive_existingDebt_removesAndReturnsTrue() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        boolean result = debtManager.forgive(creditorId, debtorId);
        assertTrue(result);
        assertFalse(debtManager.isDebtor(debtorId));
    }

    @Test
    void forgive_noDebt_returnsFalse() {
        boolean result = debtManager.forgive(creditorId, debtorId);
        assertFalse(result);
    }

    @Test
    void forgive_wrongCreditor_returnsFalse() {
        UUID otherCreditor = UUID.randomUUID();
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 60000);
        boolean result = debtManager.forgive(otherCreditor, debtorId);
        assertFalse(result);
        assertTrue(debtManager.isDebtor(debtorId));
    }

    // --- clearDebtsForDebtor ---

    @Test
    void clearDebtsForDebtor_removesAllDebts() {
        UUID creditor2 = UUID.randomUUID();
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        debtManager.addDebt(creditor2, debtorId, 300, System.currentTimeMillis() + 60000);
        debtManager.clearDebtsForDebtor(debtorId);
        assertFalse(debtManager.isDebtor(debtorId));
        assertTrue(debtManager.getDebtsForPlayer(debtorId).isEmpty());
    }

    @Test
    void clearDebtsForDebtor_doesNotAffectOthers() {
        UUID debtor2 = UUID.randomUUID();
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        debtManager.addDebt(creditorId, debtor2, 300, System.currentTimeMillis() + 60000);
        debtManager.clearDebtsForDebtor(debtorId);
        assertFalse(debtManager.isDebtor(debtorId));
        assertTrue(debtManager.isDebtor(debtor2));
    }

    // --- checkDeadlines ---

    @Test
    void checkDeadlines_expiredDebt_callsSetCriminal() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() - 1000);
        // Bukkit.getPlayer() returns null (mock server returns null by default)
        debtManager.checkDeadlines();
        verify(criminalManager).setCriminal(debtorId, true);
        assertFalse(debtManager.isDebtor(debtorId));
    }

    @Test
    void checkDeadlines_notExpired_doesNothing() {
        debtManager.addDebt(creditorId, debtorId, 1000, System.currentTimeMillis() + 600000);
        debtManager.checkDeadlines();
        verify(criminalManager, never()).setCriminal(any(), anyBoolean());
        assertTrue(debtManager.isDebtor(debtorId));
    }

    // --- 複数債務 ---

    @Test
    void multipleDebts_sameDebtorDifferentCreditors() {
        UUID creditor2 = UUID.randomUUID();
        debtManager.addDebt(creditorId, debtorId, 500, System.currentTimeMillis() + 60000);
        debtManager.addDebt(creditor2, debtorId, 300, System.currentTimeMillis() + 60000);
        List<DebtManager.Debt> debts = debtManager.getDebtsForPlayer(debtorId);
        assertEquals(2, debts.size());
        assertEquals(creditorId, debtManager.getCreditor(debtorId));
    }

    // --- loadDebts ---

    @Test
    void constructor_loadsExistingDebts() {
        List<Map<String, Object>> existingDebts = new ArrayList<>();
        Map<String, Object> debtMap = new HashMap<>();
        debtMap.put("creditor", creditorId.toString());
        debtMap.put("debtor", debtorId.toString());
        debtMap.put("amount", 750.0);
        debtMap.put("deadline", System.currentTimeMillis() + 60000L);
        existingDebts.add(debtMap);
        when(dataManager.loadDebts()).thenReturn(existingDebts);
        DebtManager loadedManager = new DebtManager(plugin, dataManager, criminalManager, nametagManager);
        assertTrue(loadedManager.isDebtor(debtorId));
        assertEquals(creditorId, loadedManager.getCreditor(debtorId));
    }
}
