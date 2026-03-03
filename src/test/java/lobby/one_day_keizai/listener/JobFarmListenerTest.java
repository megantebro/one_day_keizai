package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobFarmListenerTest {

    private static final String SAFE_WORLD = "economy";
    private static final String PVP_WORLD  = "world";

    @Mock private JobManager jobManager;
    @Mock private Player player;
    @Mock private BlockPlaceEvent event;
    @Mock private Block block;
    @Mock private World safeWorld;
    @Mock private World pvpWorld;

    private JobFarmListener listener;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new JobFarmListener(jobManager, SAFE_WORLD, null);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        lenient().when(event.getPlayer()).thenReturn(player);
        lenient().when(event.getBlockPlaced()).thenReturn(block);
        lenient().when(block.getWorld()).thenReturn(safeWorld);
        lenient().when(safeWorld.getName()).thenReturn(SAFE_WORLD);
        lenient().when(pvpWorld.getName()).thenReturn(PVP_WORLD);
    }

    // --- 安全ワールドでの農作物制限 ---

    @Test
    void onBlockPlace_wheatSeeds_nonFarmer_cancels() {
        when(block.getType()).thenReturn(Material.WHEAT);
        when(jobManager.isFarmer(playerId)).thenReturn(false);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(contains("農家のみ"));
    }

    @Test
    void onBlockPlace_wheatSeeds_farmer_doesNotCancel() {
        when(block.getType()).thenReturn(Material.WHEAT);
        when(jobManager.isFarmer(playerId)).thenReturn(true);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onBlockPlace_carrot_nonFarmer_cancels() {
        when(block.getType()).thenReturn(Material.CARROTS);
        when(jobManager.isFarmer(playerId)).thenReturn(false);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onBlockPlace_sugarCane_nonFarmer_cancels() {
        when(block.getType()).thenReturn(Material.SUGAR_CANE);
        when(jobManager.isFarmer(playerId)).thenReturn(false);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
    }

    // --- オーバーワールドでは農作物の植え付けは自由（収穫制限との対称性）---

    @Test
    void onBlockPlace_cropInOverworld_nonFarmer_notCancelled() {
        when(block.getWorld()).thenReturn(pvpWorld);
        when(block.getType()).thenReturn(Material.WHEAT);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }

    // --- 木材・その他は制限なし ---

    @Test
    void onBlockPlace_oakSapling_notRestricted() {
        when(block.getType()).thenReturn(Material.OAK_SAPLING);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onBlockPlace_dirt_notRestricted() {
        when(block.getType()).thenReturn(Material.DIRT);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
    }
}
