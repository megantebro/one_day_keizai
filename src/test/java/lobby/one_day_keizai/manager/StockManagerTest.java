package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.StockDataManager;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.stock.StockOffer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockManagerTest {

    @Mock private JavaPlugin plugin;
    @Mock private Economy economy;
    @Mock private JobManager jobManager;
    @Mock private StockDataManager stockData;
    @Mock private Player player;
    @Mock private OfflinePlayer taxPlayer;

    private StockManager stockManager;
    private final UUID playerId = UUID.randomUUID();

    private static final double SELL_FEE      = 0.20;
    private static final double DIVIDEND_RATE = 0.01;
    private static final int    DIV_INTERVAL  = 60;
    private static final double TAX_BALANCE   = 1_000_000.0;
    private static final double STOCK_PRICE   = TAX_BALANCE * 0.01; // 10,000

    @BeforeEach
    void setUp() {
        stockManager = new StockManager(plugin, economy, jobManager, stockData,
                SELL_FEE, DIVIDEND_RATE, DIV_INTERVAL);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
    }

    // ─── 株価計算 ────────────────────────────────────────────────────────────

    @Test
    void getStockPrice_isTaxBalanceTimes1Percent() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            when(economy.getBalance(taxPlayer)).thenReturn(TAX_BALANCE);

            assertEquals(STOCK_PRICE, stockManager.getStockPrice(), 0.001);
        }
    }

    // ─── 購入 ─────────────────────────────────────────────────────────────────

    @Test
    void buyStock_nonCapitalist_returnsError() {
        // 職業チェックは getStockPrice() より前に実行されるのでBukkitモック不要
        when(jobManager.isCapitalist(playerId)).thenReturn(false);

        String err = stockManager.buyStock(player, 1);
        assertNotNull(err);
        assertTrue(err.contains("資本家"));
    }

    @Test
    void buyStock_insufficientBalance_returnsError() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            when(jobManager.isCapitalist(playerId)).thenReturn(true);
            when(economy.getBalance(taxPlayer)).thenReturn(TAX_BALANCE);
            when(economy.getBalance(player)).thenReturn(5_000.0); // 10,000G 不足

            String err = stockManager.buyStock(player, 1);
            assertNotNull(err);
            assertTrue(err.contains("残高不足"));
        }
    }

    @Test
    void buyStock_success_withdrawsFromPlayerAndDepositToTax() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            when(jobManager.isCapitalist(playerId)).thenReturn(true);
            when(economy.getBalance(taxPlayer)).thenReturn(TAX_BALANCE);
            when(economy.getBalance(player)).thenReturn(100_000.0);
            when(economy.withdrawPlayer(player, STOCK_PRICE))
                    .thenReturn(new EconomyResponse(STOCK_PRICE, 90_000,
                            EconomyResponse.ResponseType.SUCCESS, null));
            when(stockData.getHoldings(playerId)).thenReturn(1);

            String err = stockManager.buyStock(player, 1);
            assertNull(err);

            verify(economy).withdrawPlayer(player, STOCK_PRICE);
            verify(economy).depositPlayer(taxPlayer, STOCK_PRICE);
            verify(stockData).addHoldings(playerId, 1);
            verify(stockData).save();
        }
    }

    @Test
    void buyStock_zeroAmount_returnsError() {
        String err = stockManager.buyStock(player, 0);
        assertNotNull(err);
    }

    // ─── システム売却 ─────────────────────────────────────────────────────────

    @Test
    void sellToSystem_insufficientHoldings_returnsError() {
        // holdings チェックは getStockPrice() より先に実行されるのでBukkitモック不要
        when(stockData.getHoldings(playerId)).thenReturn(2);

        String err = stockManager.sellToSystem(player, 5);
        assertNotNull(err);
        assertTrue(err.contains("保有数が不足"));
    }

    @Test
    void sellToSystem_success_correctMoneyFlow() {
        // 1口売却: 株価=10,000, 手数料=2,000, 受取=8,000
        double gross = STOCK_PRICE;
        double fee   = gross * SELL_FEE;  // 2,000
        double net   = gross - fee;        // 8,000

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            when(economy.getBalance(taxPlayer)).thenReturn(TAX_BALANCE);
            when(stockData.getHoldings(playerId)).thenReturn(3);
            when(economy.withdrawPlayer(taxPlayer, gross))
                    .thenReturn(new EconomyResponse(gross, TAX_BALANCE - gross,
                            EconomyResponse.ResponseType.SUCCESS, null));
            when(stockData.getHoldings(playerId)).thenReturn(2);

            String err = stockManager.sellToSystem(player, 1);
            assertNull(err);

            verify(economy).withdrawPlayer(taxPlayer, gross);
            verify(economy).depositPlayer(player, net);
            verify(economy).depositPlayer(taxPlayer, fee);
            verify(stockData).addHoldings(playerId, -1);
        }
    }

    @Test
    void sellToSystem_insufficientTax_returnsError() {
        // Tax=5G, 株価=0.05G, 200口売却 → gross=10G > Tax=5G → エラー
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            when(economy.getBalance(taxPlayer)).thenReturn(5.0);
            when(stockData.getHoldings(playerId)).thenReturn(200);

            String err = stockManager.sellToSystem(player, 200);
            assertNotNull(err);
            assertTrue(err.contains("Tax 残高が不足"));
        }
    }

    // ─── P2P オファー ─────────────────────────────────────────────────────────

    @Test
    void createOffer_selfTarget_returnsError() {
        // 自己チェックは getStockPrice() より先 → Bukkitモック不要
        String err = stockManager.createOffer(player, player, 1, 0);
        assertNotNull(err);
        assertTrue(err.contains("自分自身"));
    }

    @Test
    void createOffer_insufficientHoldings_returnsError() {
        // holdings チェックは getStockPrice() より先 → Bukkitモック不要
        // target.getUniqueId() は holdings チェック失敗後には呼ばれない
        Player target = mock(Player.class);
        when(stockData.getHoldings(playerId)).thenReturn(0);

        String err = stockManager.createOffer(player, target, 1, 0);
        assertNotNull(err);
        assertTrue(err.contains("保有数が不足"));
    }

    // ─── 配当 ─────────────────────────────────────────────────────────────────

    @Test
    void distributeDividends_emptyHoldings_doesNothing() {
        // getAllHoldings() が先に呼ばれ isEmpty なら早期リターン → Bukkitモック不要
        when(stockData.getAllHoldings()).thenReturn(new HashMap<>());

        stockManager.distributeDividends();

        verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void distributeDividends_paysCorrectAmount() {
        UUID holderA = UUID.randomUUID();
        UUID holderB = UUID.randomUUID();
        Map<UUID, Integer> holdings = new HashMap<>();
        holdings.put(holderA, 3);
        holdings.put(holderB, 2);

        OfflinePlayer offlineA = mock(OfflinePlayer.class);
        OfflinePlayer offlineB = mock(OfflinePlayer.class);

        double expectedPerShare = STOCK_PRICE * DIVIDEND_RATE; // 100G/株
        double expectedPayoutA  = expectedPerShare * 3;         // 300G
        double expectedPayoutB  = expectedPerShare * 2;         // 200G

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("tax")).thenReturn(taxPlayer);
            bukkit.when(() -> Bukkit.getOfflinePlayer(holderA)).thenReturn(offlineA);
            bukkit.when(() -> Bukkit.getOfflinePlayer(holderB)).thenReturn(offlineB);
            bukkit.when(() -> Bukkit.broadcastMessage(anyString())).thenReturn(0);

            when(economy.getBalance(taxPlayer)).thenReturn(TAX_BALANCE);
            when(stockData.getAllHoldings()).thenReturn(holdings);
            when(economy.withdrawPlayer(eq(taxPlayer), anyDouble()))
                    .thenReturn(new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null));

            stockManager.distributeDividends();

            verify(economy).depositPlayer(offlineA, expectedPayoutA);
            verify(economy).depositPlayer(offlineB, expectedPayoutB);
        }
    }
}
