package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.NametagManager;
import lobby.one_day_keizai.ui.JobSelectionUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSelectionListenerTest {

    @Mock private JobManager jobManager;
    @Mock private NametagManager nametagManager;
    @Mock private Player player;
    @Mock private InventoryView view;
    @Mock private Inventory inventory;

    private JobSelectionListener listener;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new JobSelectionListener(jobManager, nametagManager);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(jobManager.getJob(playerId)).thenReturn(Job.NONE);
    }

    private InventoryClickEvent makeClickEvent(int rawSlot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTitle()).thenReturn(JobSelectionUI.TITLE);
        when(event.getRawSlot()).thenReturn(rawSlot);
        return event;
    }

    @Test
    void clickFarmerSlot_setsJobFarmer() {
        InventoryClickEvent event = makeClickEvent(JobSelectionUI.SLOT_FARMER);

        listener.onInventoryClick(event);

        verify(jobManager).setJob(playerId, Job.FARMER);
        verify(event).setCancelled(true);
        verify(player).closeInventory();
    }

    @Test
    void clickBlacksmithSlot_setsJobBlacksmith() {
        InventoryClickEvent event = makeClickEvent(JobSelectionUI.SLOT_BLACKSMITH);

        listener.onInventoryClick(event);

        verify(jobManager).setJob(playerId, Job.BLACKSMITH);
        verify(player).closeInventory();
    }

    @Test
    void clickMerchantSlot_setsJobMerchant() {
        InventoryClickEvent event = makeClickEvent(JobSelectionUI.SLOT_MERCHANT);

        listener.onInventoryClick(event);

        verify(jobManager).setJob(playerId, Job.MERCHANT);
        verify(player).closeInventory();
    }

    @Test
    void clickLaterSlot_doesNotSetJob() {
        InventoryClickEvent event = makeClickEvent(JobSelectionUI.SLOT_LATER);

        listener.onInventoryClick(event);

        verify(jobManager, never()).setJob(any(), any());
        verify(player).closeInventory();
    }

    @Test
    void clickGlassSlot_doesNothing() {
        InventoryClickEvent event = makeClickEvent(0); // ガラスパネルのスロット

        listener.onInventoryClick(event);

        verify(jobManager, never()).setJob(any(), any());
        verify(player, never()).closeInventory();
    }

    @Test
    void clickInDifferentUI_ignored() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTitle()).thenReturn("別のインベントリ");

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
        verify(jobManager, never()).setJob(any(), any());
    }
}
