package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.ui.JobSelectionUI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 職業選択UIのクリックイベントを処理する。
 */
public class JobSelectionListener implements Listener {

    private final JobManager jobManager;

    public JobSelectionListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 職業選択UIかどうか確認
        if (!JobSelectionUI.isJobSelectionUI(event.getView().getTitle())) return;

        // アイテムの移動を全てキャンセル
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // 後で選ぶ
        if (slot == JobSelectionUI.SLOT_LATER) {
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "職業は後で /job select <職業名> で選べます。");
            return;
        }

        // 職業ボタン
        Job job = JobSelectionUI.getJobForSlot(slot);
        if (job == null) return; // 関係ないスロット

        jobManager.setJob(player.getUniqueId(), job);
        player.closeInventory();

        player.sendMessage(ChatColor.GREEN + "══════════════════════════════");
        player.sendMessage(ChatColor.GREEN + "職業を " + job.getColorCode() + "【" + job.getDisplayName() + "】"
                + ChatColor.GREEN + " に設定しました！");
        sendJobWelcome(player, job);
        player.sendMessage(ChatColor.GREEN + "══════════════════════════════");
    }

    /** プレイヤーがドラッグでアイテムを動かすのを防ぐ。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (JobSelectionUI.isJobSelectionUI(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    private void sendJobWelcome(Player player, Job job) {
        switch (job) {
            case FARMER -> {
                player.sendMessage(ChatColor.GREEN + "  ・安全ワールドで農作物を栽培できます");
                player.sendMessage(ChatColor.GREEN + "  ・農作物をショップで売却できます（準備中）");
            }
            case BLACKSMITH -> {
                player.sendMessage(ChatColor.GOLD + "  ・鉄以上のツール/装備をクラフトできます");
                player.sendMessage(ChatColor.GOLD + "  ・装備をショップで売却できます（準備中）");
            }
            case MERCHANT -> {
                player.sendMessage(ChatColor.AQUA + "  ・危険ワールドの遠方ショップにアクセスできます（準備中）");
            }
            default -> {}
        }
    }
}
