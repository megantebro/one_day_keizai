package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
    }

    @Test
    void onEnchantTable_nonBlacksmith_cancels() {
        when(jobManager.isBlacksmith(playerId)).thenReturn(false);

        listener.onEnchantTableInteract(event);

        verify(event).setCancelled(true);
        verify(player, atLeastOnce()).sendMessage(contains("鍛冶屋"));
    }

    @Test
    void onEnchantTable_blacksmith_doesNotCancel() {
        when(jobManager.isBlacksmith(playerId)).thenReturn(true);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onEnchantTable_enchanter_doesNotCancel() {
        // isBlacksmith が ENCHANTER でも true を返すことをモック
        when(jobManager.isBlacksmith(playerId)).thenReturn(true);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onEnchantTable_leftClick_ignored() {
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);

        listener.onEnchantTableInteract(event);

        verify(event, never()).setCancelled(true);
        verify(jobManager, never()).isBlacksmith(any());
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
