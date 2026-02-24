package lobby.one_day_keizai.command;

import lobby.one_day_keizai.TestHelper;
import lobby.one_day_keizai.manager.DebtManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
class DebtCommandTest {

    @Mock private DebtManager debtManager;
    @Mock private Economy economy;
    @Mock private Command command;
    @Mock private Player sender;
    @Mock private Player target;
    @Mock private Server server;
    @Mock private World world;

    private DebtCommand debtCommand;

    private final UUID senderId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TestHelper.setupBukkitServer(server);
        debtCommand = new DebtCommand(debtManager, economy);
        lenient().when(sender.getUniqueId()).thenReturn(senderId);
        lenient().when(sender.getName()).thenReturn("Sender");
        lenient().when(target.getUniqueId()).thenReturn(targetId);
        lenient().when(target.getName()).thenReturn("Target");

        // 近接チェック用：同じワールドで近くにいる状態をデフォルトに
        lenient().when(sender.getWorld()).thenReturn(world);
        lenient().when(target.getWorld()).thenReturn(world);
        lenient().when(sender.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        lenient().when(target.getLocation()).thenReturn(new Location(world, 5, 64, 0));
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardownBukkitServer();
    }

    // =========================================
    // 基本テスト
    // =========================================

    @Test
    void nonPlayerSender_rejected() {
        CommandSender consoleSender = mock(CommandSender.class);
        debtCommand.onCommand(consoleSender, command, "debt", new String[]{});
        verify(consoleSender).sendMessage("プレイヤーのみ使用可能です。");
    }

    @Test
    void noArgs_showsUsage() {
        debtCommand.onCommand(sender, command, "debt", new String[]{});
        verify(sender, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    void unknownSubcommand_showsUsage() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"invalid"});
        verify(sender, atLeastOnce()).sendMessage(anyString());
    }

    // =========================================
    // lend テスト
    // =========================================

    @Test
    void lend_tooFewArgs_showsUsage() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target"});
        verify(sender).sendMessage(contains("使い方"));
    }

    @Test
    void lend_playerNotFound_showsError() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Unknown", "100", "20"});
        verify(sender).sendMessage(contains("見つかりません"));
    }

    @Test
    void lend_toSelf_showsError() {
        when(server.getPlayer("Sender")).thenReturn(sender);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Sender", "100", "20"});
        verify(sender).sendMessage(contains("自分自身"));
    }

    @Test
    void lend_invalidAmount_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "abc", "20"});
        verify(sender).sendMessage(contains("数値"));
    }

    @Test
    void lend_negativeAmount_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "-100", "20"});
        verify(sender).sendMessage(contains("正の数"));
    }

    @Test
    void lend_durationTooShort_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "100", "10"});
        verify(sender).sendMessage(contains("20分以上"));
    }

    @Test
    void lend_interestRateTooHigh_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "100", "20", "51"});
        verify(sender).sendMessage(contains("50%以下"));
    }

    @Test
    void lend_interestRateAt50_succeeds() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "100", "20", "50"});
        // リクエストが作成される（エラーメッセージが出ない）
        assertTrue(debtCommand.getPendingRequests().containsKey(targetId));
    }

    @Test
    void lend_insufficientBalance_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(50.0);
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "100", "20"});
        verify(sender).sendMessage(contains("残高が不足"));
    }

    @Test
    void lend_createsPendingRequest() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});

        // 即時実行されない
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());
        verify(debtManager, never()).addDebt(any(), any(), anyDouble(), anyLong(), anyDouble());

        // リクエストが保留中
        assertTrue(debtCommand.getPendingRequests().containsKey(targetId));
        verify(target, atLeastOnce()).sendMessage(anyString()); // 借り手に通知
    }

    @Test
    void lend_duplicateRequest_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "200", "30"});

        verify(sender).sendMessage(contains("保留中"));
    }

    // =========================================
    // accept テスト
    // =========================================

    @Test
    void accept_noPendingRequest_showsError() {
        debtCommand.onCommand(target, command, "debt", new String[]{"accept"});
        verify(target).sendMessage(contains("保留中の貸付リクエストがありません"));
    }

    @Test
    void accept_success_transfersMoneyAndAddsDebt() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        // リクエスト作成
        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});

        // 承認
        when(server.getPlayer(senderId)).thenReturn(sender);
        debtCommand.onCommand(target, command, "debt", new String[]{"accept"});

        verify(economy).withdrawPlayer(sender, 500.0);
        verify(economy).depositPlayer(target, 500.0);
        verify(debtManager).addDebt(eq(senderId), eq(targetId), eq(500.0), anyLong(), eq(0.0));
    }

    @Test
    void accept_lenderOffline_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});

        // 貸し手がオフライン
        when(server.getPlayer(senderId)).thenReturn(null);
        debtCommand.onCommand(target, command, "debt", new String[]{"accept"});

        verify(target).sendMessage(contains("オフライン"));
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    @Test
    void accept_lenderInsufficientBalance_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});

        // 承認時に残高不足
        when(server.getPlayer(senderId)).thenReturn(sender);
        when(economy.getBalance(sender)).thenReturn(100.0);
        debtCommand.onCommand(target, command, "debt", new String[]{"accept"});

        verify(target).sendMessage(contains("残高が不足"));
        verify(economy, never()).withdrawPlayer(any(Player.class), anyDouble());
    }

    // =========================================
    // deny テスト
    // =========================================

    @Test
    void deny_noPendingRequest_showsError() {
        debtCommand.onCommand(target, command, "debt", new String[]{"deny"});
        verify(target).sendMessage(contains("保留中の貸付リクエストがありません"));
    }

    @Test
    void deny_success_removesRequest() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"lend", "Target", "500", "30"});
        assertTrue(debtCommand.getPendingRequests().containsKey(targetId));

        when(server.getPlayer(senderId)).thenReturn(sender);
        debtCommand.onCommand(target, command, "debt", new String[]{"deny"});

        assertFalse(debtCommand.getPendingRequests().containsKey(targetId));
        verify(target).sendMessage(contains("拒否しました"));
        verify(sender).sendMessage(contains("拒否しました"));
    }

    // =========================================
    // repay テスト
    // =========================================

    @Test
    void repay_tooFewArgs_showsUsage() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"repay", "Target"});
        verify(sender).sendMessage(contains("使い方"));
    }

    @Test
    void repay_noDebt_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);
        when(debtManager.repay(senderId, targetId, 500.0)).thenReturn(0.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"repay", "Target", "500"});

        verify(sender).sendMessage(contains("債務がありません"));
    }

    @Test
    void repay_success_transfersMoney() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(economy.getBalance(sender)).thenReturn(1000.0);
        when(debtManager.repay(senderId, targetId, 500.0)).thenReturn(500.0);

        debtCommand.onCommand(sender, command, "debt", new String[]{"repay", "Target", "500"});

        verify(economy).withdrawPlayer(sender, 500.0);
        verify(economy).depositPlayer(target, 500.0);
    }

    // =========================================
    // forgive テスト
    // =========================================

    @Test
    void forgive_tooFewArgs_showsUsage() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"forgive"});
        verify(sender).sendMessage(contains("使い方"));
    }

    @Test
    void forgive_playerNotFound_showsError() {
        debtCommand.onCommand(sender, command, "debt", new String[]{"forgive", "Unknown"});
        verify(sender).sendMessage(contains("見つかりません"));
    }

    @Test
    void forgive_noDebt_showsError() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(debtManager.forgive(senderId, targetId)).thenReturn(false);

        debtCommand.onCommand(sender, command, "debt", new String[]{"forgive", "Target"});

        verify(sender).sendMessage(contains("債権がありません"));
    }

    @Test
    void forgive_success_notifiesBothPlayers() {
        when(server.getPlayer("Target")).thenReturn(target);
        when(debtManager.forgive(senderId, targetId)).thenReturn(true);

        debtCommand.onCommand(sender, command, "debt", new String[]{"forgive", "Target"});

        verify(sender).sendMessage(contains("許しました"));
        verify(target).sendMessage(contains("許しました"));
    }

    // =========================================
    // list テスト
    // =========================================

    @Test
    void list_noDebts_showsMessage() {
        when(debtManager.getDebtsForPlayer(senderId)).thenReturn(new ArrayList<>());

        debtCommand.onCommand(sender, command, "debt", new String[]{"list"});

        verify(sender).sendMessage(contains("ありません"));
    }

    // =========================================
    // TabComplete テスト
    // =========================================

    @Test
    void tabComplete_firstArg_returnsSubcommands() {
        List<String> completions = debtCommand.onTabComplete(sender, command, "debt", new String[]{""});
        assertEquals(6, completions.size());
        assertTrue(completions.contains("lend"));
        assertTrue(completions.contains("accept"));
        assertTrue(completions.contains("deny"));
        assertTrue(completions.contains("repay"));
        assertTrue(completions.contains("forgive"));
        assertTrue(completions.contains("list"));
    }

    @Test
    void tabComplete_firstArg_filtered() {
        List<String> completions = debtCommand.onTabComplete(sender, command, "debt", new String[]{"le"});
        assertEquals(1, completions.size());
        assertTrue(completions.contains("lend"));
    }

    @Test
    void tabComplete_secondArgForList_returnsEmpty() {
        List<String> completions = debtCommand.onTabComplete(sender, command, "debt", new String[]{"list", ""});
        assertTrue(completions.isEmpty());
    }
}
