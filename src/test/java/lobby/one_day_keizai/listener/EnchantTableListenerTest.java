package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnchantTableListenerTest {

    @Mock private JobManager jobManager;
    @Mock private Player player;
    @Mock private Block block;
    @Mock private PlayerInteractEvent event;

    private EnchantTableListener listener;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new EnchantTableListener(jobManager);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(event.getPlayer()).thenReturn(player);
        lenient().when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        lenient().when(event.getClickedBlock()).thenReturn(block);
        lenient().when(block.getType()).thenReturn(Material.ENCHANTING_TABLE);
        lenient().when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    }

    @Test
    void onEnchantTable_nonEnchanter_cancels() {
        when(jobManager.isEnchanter(playerId)).thenReturn(false);

        listener.onEnchantTableInteract(event);

        verify(event).setCancelled(true);
        verify(player, atLeastOnce()).sendMessage(contains("エンチャンター"));
    }

    @Test
    void onEnchantTable_blacksmith_cancels() {
        // 鍛冶師はエンチャント台使用不可
        when(jobManager.isEnchanter(playerId)).thenReturn(false);

        listener.onEnchantTableInteract(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onEnchantTable_enchanter_doesNotCancel() {
        when(jobManager.isEnchanter(playerId)).thenReturn(true);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onEnchantTable_leftClick_ignored() {
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
        verify(jobManager, never()).isEnchanter(any());
    }

    @Test
    void onEnchantTable_otherBlock_ignored() {
        when(block.getType()).thenReturn(Material.CRAFTING_TABLE);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onEnchantTable_nullBlock_ignored() {
        when(event.getClickedBlock()).thenReturn(null);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
    }
}
