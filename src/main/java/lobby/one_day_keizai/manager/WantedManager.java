package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指名手配システム管理。
 * オーバーワールドで1キルすると指名手配状態になり、一定時間安全ワールドに戻れなくなる。
 *
 * 懸賞金の扱い（物理アイテムなし）:
 *   - 時効成立 → 懸賞金を「未払いキュー」に積む
 *   - 安全ワールドに帰還した瞬間に WantedListener が自動入金
 *   - 指名手配中に死亡した場合は懸賞金消滅（デポジット没収のみ、二重払いなし）
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
    private final Economy economy;
    private final PlayerDataManager playerDataManager;
    private final int wantedDurationSeconds;

    /** 現在指名手配中のプレイヤー */
    private final Map<UUID, WantedEntry> wantedPlayers = new ConcurrentHashMap<>();

    /**
     * 時効成立後、安全ワールド帰還時に支払う懸賞金の未払いキュー。
     * デポジットは expireWanted 時点でクリア済みなので二重払いなし。
     * key=UUID, value=懸賞金額
     */
    private final Map<UUID, Double> pendingPayouts = new ConcurrentHashMap<>();

    public WantedManager(JavaPlugin plugin, NametagManager nametagManager,
                         Economy economy, PlayerDataManager playerDataManager,
                         int wantedDurationSeconds) {
        this.plugin = plugin;
        this.nametagManager = nametagManager;
        this.economy = economy;
        this.playerDataManager = playerDataManager;
        this.wantedDurationSeconds = wantedDurationSeconds;
    }

    // ─── 指名手配 ────────────────────────────────────────────────────────────

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
     * 時効による指名手配解除。
     *
     * デポジットを playerdata から即クリアして pendingPayouts に移す。
     * これにより returnToSafeWorld での通常返金は 0 になり、
     * 安全ワールド帰還時に pendingPayouts 分だけ支払われる（二重払いなし）。
     */
    private void expireWanted(UUID uuid) {
        WantedEntry entry = wantedPlayers.remove(uuid);
        if (entry == null) return;

        // デポジットを playerdata からクリアし、pending payout に移す
        // (returnToSafeWorld での通常返金を防ぐため)
        double deposit = playerDataManager.getOverworldDeposit(uuid);
        playerDataManager.clearOverworldDeposit(uuid);
        playerDataManager.save();

        double payout = deposit > 0 ? deposit : entry.bounty;
        if (payout > 0) {
            pendingPayouts.merge(uuid, payout, Double::sum);
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            nametagManager.clearWanted(player);
            player.sendMessage(ChatColor.GREEN + "指名手配の時効が成立しました！");
            player.sendMessage(ChatColor.YELLOW + "安全ワールドに帰還すると入場料 "
                    + ChatColor.GOLD + "$" + String.format("%.0f", payout)
                    + ChatColor.YELLOW + " が全額返金されます。");
        }
    }

    /**
     * 指名手配犯がオーバーワールドで死亡した時に呼ぶ。
     * キルした相手は PvPListener で被害者のデポジット（入場料）を受け取る。
     * 懸賞金の二重払いを防ぐため、ここでは金銭処理は行わない。
     */
    public void handleWantedDeath(Player victim, Player killer) {
        UUID victimId = victim.getUniqueId();
        WantedEntry entry = wantedPlayers.remove(victimId);
        if (entry == null) return;
        entry.expiryTask.cancel();
        // ネームタグ即時リセット（リスポーン時の setNormal でも再度リセットされるが念のため）
        nametagManager.clearWanted(victim);
    }

    // ─── 未払いキュー ─────────────────────────────────────────────────────────

    /**
     * 未払い懸賞金があるか確認。
     */
    public boolean hasPendingPayout(UUID uuid) {
        return pendingPayouts.containsKey(uuid);
    }

    /**
     * 未払い懸賞金を入金して、キューから削除する。
     * WantedListener の onPlayerChangedWorld から呼び出す。
     */
    public void collectPendingPayout(Player player) {
        Double amount = pendingPayouts.remove(player.getUniqueId());
        if (amount == null || amount <= 0) return;

        economy.depositPlayer(player, amount);
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GOLD + "  [懸賞金回収] 生き延びた報酬を受け取りました！");
        player.sendMessage(ChatColor.WHITE + "  +" + String.format("%.0f", amount) + " G");
        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ─── 状態確認 ────────────────────────────────────────────────────────────

    public boolean isWanted(UUID uuid) {
        return wantedPlayers.containsKey(uuid);
    }

    /** 現在指名手配中の全UUID */
    public Set<UUID> getWantedUUIDs() {
        return Collections.unmodifiableSet(wantedPlayers.keySet());
    }

    public double getBounty(UUID uuid) {
        WantedEntry entry = wantedPlayers.get(uuid);
        return entry != null ? entry.bounty : 0;
    }

    /**
     * 指名手配を即時解除（タイマーもキャンセル）。懸賞金は消滅。
     */
    public void clearWanted(UUID uuid) {
        WantedEntry entry = wantedPlayers.remove(uuid);
        if (entry != null) entry.expiryTask.cancel();
        // オンラインならネームタグも即時リセット
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) nametagManager.clearWanted(player);
    }

    /**
     * 定期的にネームタグ状態を実態（wantedPlayers）と同期するタスクを開始する。
     * プラグインリロード後や何らかの理由でネームタグが更新されなかった場合のフェイルセーフ。
     * 5秒ごとに全オンラインプレイヤーを走査して正しい色に戻す。
     */
    public void startSyncTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean isWanted = wantedPlayers.containsKey(player.getUniqueId());
                    nametagManager.updateNametag(player, isWanted);
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // 5秒ごと
    }

    /**
     * 指名手配中プレイヤーが地下（Y &lt; 0）にいるとダメージを与えるタスク。
     * 毎秒チェック。PvPワールド限定。
     *
     * @param pvpWorldName 対象のPvPワールド名
     */
    public void startUndergroundDamageTask(String pvpWorldName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!wantedPlayers.containsKey(player.getUniqueId())) continue;
                    if (player.getWorld() == null) continue;
                    if (!player.getWorld().getName().equals(pvpWorldName)) continue;
                    if (player.getLocation().getY() >= 0) continue;

                    // Y=0未満で毎秒1ダメージ（半ハート）
                    player.damage(1.0);
                    player.sendMessage(ChatColor.RED + "⚠ 指名手配中は地下に隠れられません！");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒ごと
    }
}
