package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.TestHelper;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private Economy economy;
    @Mock private Server server;
    @Mock private ItemFactory itemFactory;
    @Mock private EnchantmentStorageMeta enchantMeta;
    @Mock private BukkitScheduler scheduler;

    private AuctionManager auctionManager;

    @BeforeEach
    void setUp() {
        TestHelper.setupBukkitServer(server);
        when(server.getItemFactory()).thenReturn(itemFactory);
        when(itemFactory.getItemMeta(Material.ENCHANTED_BOOK)).thenReturn(enchantMeta);
        lenient().when(itemFactory.isApplicable(any(), any(Material.class))).thenReturn(true);
        lenient().when(itemFactory.asMetaFor(any(), any(Material.class))).thenReturn(enchantMeta);
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        auctionManager = new AuctionManager(plugin, economy, 30, 120);
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardownBukkitServer();
    }

    // --- アイテムプール ---

    @Test
    void itemPool_hasSixItems() {
        assertEquals(6, auctionManager.getItemPool().size());
    }

    // --- startAuction ---

    @Test
    void startAuction_setsActiveAndItem() {
        auctionManager.startAuction();
        assertTrue(auctionManager.isAuctionActive());
        assertNotNull(auctionManager.getCurrentItemName());
    }

    @Test
    void startAuction_whileActive_doesNothing() {
        auctionManager.startAuction();
        String firstItem = auctionManager.getCurrentItemName();
        auctionManager.startAuction(); // 二重開始を試みる
        assertTrue(auctionManager.isAuctionActive());
        assertEquals(firstItem, auctionManager.getCurrentItemName());
    }

    // --- placeBid ---

    @Test
    void placeBid_notActive_sendsError() {
        Player player = mockPlayer(UUID.randomUUID(), "TestPlayer");
        auctionManager.placeBid(player, 100);
        verify(player).sendMessage(contains("開催されていません"));
    }

    @Test
    void placeBid_negativeAmount_sendsError() {
        auctionManager.startAuction();
        Player player = mockPlayer(UUID.randomUUID(), "TestPlayer");
        auctionManager.placeBid(player, -50);
        verify(player).sendMessage(contains("正の数"));
    }

    @Test
    void placeBid_zeroAmount_sendsError() {
        auctionManager.startAuction();
        Player player = mockPlayer(UUID.randomUUID(), "TestPlayer");
        auctionManager.placeBid(player, 0);
        verify(player).sendMessage(contains("正の数"));
    }

    @Test
    void placeBid_insufficientBalance_sendsError() {
        auctionManager.startAuction();
        Player player = mockPlayer(UUID.randomUUID(), "TestPlayer");
        when(economy.getBalance(player)).thenReturn(50.0);
        auctionManager.placeBid(player, 100);
        verify(player).sendMessage(contains("入札上限"));
    }

    @Test
    void placeBid_validBid_recordsBid() {
        auctionManager.startAuction();
        Player player = mockPlayer(UUID.randomUUID(), "TestPlayer");
        when(economy.getBalance(player)).thenReturn(1000.0);
        auctionManager.placeBid(player, 500);
        assertEquals(500, auctionManager.getHighestBid());
        assertEquals(1, auctionManager.getBids().size());
    }

    @Test
    void placeBid_lowerThanPrevious_sendsError() {
        auctionManager.startAuction();
        UUID playerId = UUID.randomUUID();
        Player player = mockPlayer(playerId, "TestPlayer");
        when(economy.getBalance(player)).thenReturn(1000.0);
        auctionManager.placeBid(player, 500);
        auctionManager.placeBid(player, 300);
        verify(player).sendMessage(contains("前回の入札額"));
        assertEquals(500, auctionManager.getHighestBid());
    }

    @Test
    void placeBid_equalToPrevious_sendsError() {
        auctionManager.startAuction();
        UUID playerId = UUID.randomUUID();
        Player player = mockPlayer(playerId, "TestPlayer");
        when(economy.getBalance(player)).thenReturn(1000.0);
        auctionManager.placeBid(player, 500);
        auctionManager.placeBid(player, 500);
        verify(player).sendMessage(contains("前回の入札額"));
    }

    @Test
    void placeBid_higherThanPrevious_updatesBid() {
        auctionManager.startAuction();
        UUID playerId = UUID.randomUUID();
        Player player = mockPlayer(playerId, "TestPlayer");
        when(economy.getBalance(player)).thenReturn(1000.0);
        auctionManager.placeBid(player, 500);
        auctionManager.placeBid(player, 700);
        assertEquals(700, auctionManager.getHighestBid());
    }

    @Test
    void placeBid_lowerThanOtherPlayerHighest_sendsError() {
        auctionManager.startAuction();
        Player player1 = mockPlayer(UUID.randomUUID(), "Player1");
        Player player2 = mockPlayer(UUID.randomUUID(), "Player2");
        when(economy.getBalance(player1)).thenReturn(1000.0);
        when(economy.getBalance(player2)).thenReturn(1000.0);
        auctionManager.placeBid(player1, 500);
        auctionManager.placeBid(player2, 300);
        verify(player2).sendMessage(contains("最高入札額"));
        assertEquals(1, auctionManager.getBids().size());
    }

    @Test
    void placeBid_multiplePlayers_tracksAll() {
        auctionManager.startAuction();
        Player player1 = mockPlayer(UUID.randomUUID(), "Player1");
        Player player2 = mockPlayer(UUID.randomUUID(), "Player2");
        when(economy.getBalance(player1)).thenReturn(1000.0);
        when(economy.getBalance(player2)).thenReturn(1000.0);
        auctionManager.placeBid(player1, 500);
        auctionManager.placeBid(player2, 600);
        assertEquals(2, auctionManager.getBids().size());
        assertEquals(600, auctionManager.getHighestBid());
    }

    // --- endAuction ---

    @Test
    void endAuction_noBids_announcesNoBidder() {
        auctionManager.startAuction();
        auctionManager.endAuction();
        assertFalse(auctionManager.isAuctionActive());
    }

    @Test
    void endAuction_withBids_withdrawsFromWinner() {
        auctionManager.startAuction();
        UUID winnerId = UUID.randomUUID();
        Player winner = mockPlayer(winnerId, "Winner");
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(winner.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(winner.isOnline()).thenReturn(true);
        when(economy.getBalance(winner)).thenReturn(1000.0);
        when(server.getPlayer(winnerId)).thenReturn(winner);
        when(server.getOfflinePlayer(winnerId)).thenReturn(winner);

        auctionManager.placeBid(winner, 500);
        auctionManager.endAuction();

        verify(economy).withdrawPlayer(winner, 500.0);
        assertFalse(auctionManager.isAuctionActive());
    }

    @Test
    void endAuction_highestBidderWins() {
        auctionManager.startAuction();
        UUID player1Id = UUID.randomUUID();
        UUID player2Id = UUID.randomUUID();
        Player player1 = mockPlayer(player1Id, "Player1");
        Player player2 = mockPlayer(player2Id, "Player2");
        PlayerInventory inventory2 = mock(PlayerInventory.class);
        when(player2.getInventory()).thenReturn(inventory2);
        when(inventory2.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(player2.isOnline()).thenReturn(true);
        when(economy.getBalance(player1)).thenReturn(1000.0);
        when(economy.getBalance(player2)).thenReturn(1000.0);
        when(server.getPlayer(player2Id)).thenReturn(player2);
        when(server.getOfflinePlayer(player2Id)).thenReturn(player2);

        auctionManager.placeBid(player1, 500);
        auctionManager.placeBid(player2, 800);
        auctionManager.endAuction();

        // player2 が最高額なので引き落とし
        verify(economy).withdrawPlayer(player2, 800.0);
        assertFalse(auctionManager.isAuctionActive());
    }

    @Test
    void endAuction_notActive_doesNothing() {
        // オークション開始していない状態で endAuction を呼んでもエラーにならない
        auctionManager.endAuction();
        assertFalse(auctionManager.isAuctionActive());
    }

    @Test
    void endAuction_resetsState() {
        auctionManager.startAuction();
        auctionManager.endAuction();
        assertFalse(auctionManager.isAuctionActive());
        assertNull(auctionManager.getCurrentItemName());
        assertEquals(0, auctionManager.getHighestBid());
    }

    // --- getHighestBid ---

    @Test
    void getHighestBid_noBids_returnsZero() {
        assertEquals(0, auctionManager.getHighestBid());
    }

    // --- ヘルパー ---

    private Player mockPlayer(UUID uuid, String name) {
        Player player = mock(Player.class);
        lenient().when(player.getUniqueId()).thenReturn(uuid);
        lenient().when(player.getName()).thenReturn(name);
        return player;
    }
}
