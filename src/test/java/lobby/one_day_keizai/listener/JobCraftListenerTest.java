package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobCraftListenerTest {

    @Mock private JobManager jobManager;
    @Mock private Player player;
    @Mock private CraftItemEvent event;
    @Mock private Recipe recipe;

    private JobCraftListener listener;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new JobCraftListener(jobManager);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(event.getWhoClicked()).thenReturn(player);
        lenient().when(event.getRecipe()).thenReturn(recipe);
    }

    // --- 鍛冶屋専用アイテムのクラフト ---

    @Test
    void onCraftItem_ironPickaxe_nonBlacksmith_cancels() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.IRON_PICKAXE));
        when(jobManager.isBlacksmith(playerId)).thenReturn(false);

        listener.onCraftItem(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(contains("鍛冶屋のみ"));
    }

    @Test
    void onCraftItem_ironSword_blacksmith_doesNotCancel() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.IRON_SWORD));
        when(jobManager.isBlacksmith(playerId)).thenReturn(true);

        listener.onCraftItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onCraftItem_diamondAxe_nonBlacksmith_cancels() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.DIAMOND_AXE));
        when(jobManager.isBlacksmith(playerId)).thenReturn(false);

        listener.onCraftItem(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onCraftItem_goldenHelmet_nonBlacksmith_cancels() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.GOLDEN_HELMET));
        when(jobManager.isBlacksmith(playerId)).thenReturn(false);

        listener.onCraftItem(event);

        verify(event).setCancelled(true);
    }

    // --- 全職業OK アイテム ---

    @Test
    void onCraftItem_stonePickaxe_notRestricted() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.STONE_PICKAXE));

        listener.onCraftItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onCraftItem_woodenSword_notRestricted() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.WOODEN_SWORD));

        listener.onCraftItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onCraftItem_craftingTable_notRestricted() {
        when(recipe.getResult()).thenReturn(new ItemStack(Material.CRAFTING_TABLE));

        listener.onCraftItem(event);

        verify(event, never()).setCancelled(true);
    }
}
