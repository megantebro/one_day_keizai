package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

    // ─── EnchantItemEvent (本棚ボーナス二重ガード) ──────────────────────────

    private EnchantItemEvent mockEnchantEvent(boolean isEnchanter, Material item, int expCost) {
        EnchantItemEvent e = mock(EnchantItemEvent.class);
        ItemStack stack = mock(ItemStack.class);
        lenient().when(e.getEnchanter()).thenReturn(player);
        lenient().when(jobManager.isEnchanter(playerId)).thenReturn(isEnchanter);
        lenient().when(e.getItem()).thenReturn(stack);
        lenient().when(stack.getType()).thenReturn(item);
        lenient().when(e.getExpLevelCost()).thenReturn(expCost);
        return e;
    }

    @Test
    void onEnchantItem_enchanter_armor_highLevel_cancelled() {
        EnchantItemEvent e = mockEnchantEvent(true, Material.DIAMOND_HELMET, 20);

        listener.onEnchantItem(e);

        verify(e).setCancelled(true);
        verify(player).sendMessage(contains("上限 Lv.8"));
    }

    @Test
    void onEnchantItem_enchanter_armor_lowLevel_allowed() {
        EnchantItemEvent e = mockEnchantEvent(true, Material.DIAMOND_HELMET, 7);

        listener.onEnchantItem(e);

        verify(e, never()).setCancelled(true);
    }

    @Test
    void onEnchantItem_enchanter_tool_highLevel_allowed() {
        // ツールは制限なし
        EnchantItemEvent e = mockEnchantEvent(true, Material.DIAMOND_PICKAXE, 25);

        listener.onEnchantItem(e);

        verify(e, never()).setCancelled(true);
    }

    @Test
    void onEnchantItem_nonEnchanter_notAffected() {
        EnchantItemEvent e = mockEnchantEvent(false, Material.DIAMOND_HELMET, 20);

        listener.onEnchantItem(e);

        verify(e, never()).setCancelled(true);
    }
}
