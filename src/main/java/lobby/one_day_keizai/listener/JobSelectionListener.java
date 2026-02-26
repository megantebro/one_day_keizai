package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import lobby.one_day_keizai.manager.NametagManager;
import lobby.one_day_keizai.ui.JobSelectionUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private final NametagManager nametagManager;

    public JobSelectionListener(JobManager jobManager, NametagManager nametagManager) {
        this.jobManager = jobManager;
        this.nametagManager = nametagManager;
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

        Job current = jobManager.getJob(player.getUniqueId());

        // すでに職業を持っている場合、ネザースターが必要
        if (current != Job.NONE && !current.isUpperTier()) {
            if (!consumeNetherStar(player)) {
                player.sendMessage(ChatColor.RED + "職業を変更するには " +
                        ChatColor.WHITE + "ネザースター" + ChatColor.RED + " が1つ必要です！");
                player.sendMessage(ChatColor.GRAY + "現在の職業: " + current.getColorCode() + current.getDisplayName());
                player.closeInventory();
                return;
            }
        }

        jobManager.setJob(player.getUniqueId(), job);
        nametagManager.refreshJob(player);
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

    /** インベントリからネザースターを1つ消費する。持っていない場合は false を返す。 */
    private boolean consumeNetherStar(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.NETHER_STAR) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
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
