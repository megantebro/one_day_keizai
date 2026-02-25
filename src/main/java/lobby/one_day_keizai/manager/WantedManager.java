package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.item.BountyItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指名手配システム管理。
 * オーバーワールドで1キルすると指名手配状態になり、一定時間安全ワールドに戻れなくなる。
 * 懸賞金 = 入場時に支払った入場料。
 */
public class WantedManager {

    private static class WantedEntry {
        final double bounty;
        final BukkitTask expiryTask;

        WantedEntry(double bounty, BukkitTask expiryTask) {
            this.bounty = bounty;
            this.expiryTask = expiryTask;
        }
    }

    private final JavaPlugin plugin;
    private final NametagManager nametagManager;
    private final int wantedDurationSeconds;

    private final Map<UUID, WantedEntry> wantedPlayers = new ConcurrentHashMap<>();

    public WantedManager(JavaPlugin plugin, NametagManager nametagManager, int wantedDurationSeconds) {
        this.plugin = plugin;
        this.nametagManager = nametagManager;
        this.wantedDurationSeconds = wantedDurationSeconds;
    }

    /**
     * プレイヤーを指名手配状態にする。既に指名手配中の場合はタイマーをリセット。
     * @param player 指名手配対象
     * @param bounty 懸賞金（入場料）
     */
    public void makeWanted(Player player, double bounty) {
        UUID uuid = player.getUniqueId();

        // 既存タイマーがあればキャンセル
        WantedEntry existing = wantedPlayers.remove(uuid);
        if (existing != null) existing.expiryTask.cancel();

        // 時効タスク: wantedDurationSeconds 後に自動解除
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> expireWanted(uuid),
                (long) wantedDurationSeconds * 20L);

        wantedPlayers.put(uuid, new WantedEntry(bounty, task));
        nametagManager.setWanted(player);

        int minutes = wantedDurationSeconds / 60;
        player.sendMessage(ChatColor.RED + "あなたは指名手配されました！"
                + minutes + "分間は安全ワールドに戻れません。");
        player.sendMessage(ChatColor.GOLD + "あなたの懸賞金: $" + String.format("%.0f", bounty));
        Bukkit.broadcastMessage(ChatColor.RED + "⚠ [指名手配] " + ChatColor.WHITE + player.getName()
                + ChatColor.RED + " が指名手配されました！"
                + ChatColor.GOLD + " 懸賞金: $" + String.format("%.0f", bounty));
    }

    /**
     * 時効による指名手配解除。生き延びたので懸賞金アイテムを本人に渡す。
     */
    private void expireWanted(UUID uuid) {
        WantedEntry entry = wantedPlayers.remove(uuid);
        if (entry == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            giveItem(player, BountyItem.create(entry.bounty, player.getName()));
            player.sendMessage(ChatColor.GREEN + "指名手配の時効が成立しました。懸賞金を取り戻しました。");
            nametagManager.clearWanted(player);
        }
        // オフライン時: 懸賞金は消滅（ログイン時に復元は今後の拡張で対応可）
    }

    /**
     * 指名手配犯がオーバーワールドで死亡した時に呼ぶ。
     * 懸賞金アイテムをキラーに渡し、指名手配を解除する。
     */
    public void handleWantedDeath(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();
        WantedEntry entry = wantedPlayers.remove(victimId);
        if (entry == null) return;
        entry.expiryTask.cancel();

        if (killer != null && entry.bounty > 0) {
            giveItem(killer, BountyItem.create(entry.bounty, victim.getName()));
            killer.sendMessage(ChatColor.GOLD + "指名手配犯 " + victim.getName()
                    + " を討伐！懸賞金アイテムを獲得しました。");
        }
        // ネームタグはリスポーン時に clearWanted で更新
    }

    /**
     * 指名手配状態かどうか確認。
     */
    public boolean isWanted(UUID uuid) {
        return wantedPlayers.containsKey(uuid);
    }

    /**
     * 懸賞金額を返す（指名手配中でない場合は 0）。
     */
    public double getBounty(UUID uuid) {
        WantedEntry entry = wantedPlayers.get(uuid);
        return entry != null ? entry.bounty : 0;
    }

    /**
     * 指名手配を即時解除（タイマーもキャンセル）。
     */
    public void clearWanted(UUID uuid) {
        WantedEntry entry = wantedPlayers.remove(uuid);
        if (entry != null) entry.expiryTask.cancel();
    }

    /**
     * インベントリにアイテムを追加し、入りきらない分は足元にドロップする。
     */
    private void giveItem(Player player, ItemStack item) {
        var overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
