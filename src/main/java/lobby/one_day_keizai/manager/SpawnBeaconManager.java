package lobby.one_day_keizai.manager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * オーバーワールド帰還スポットシステム。
 *
 * - ワールドスポーン上空にビーコン的なパーティクルの柱を継続表示（300ブロック四方から視認可能）
 * - スポーンから10ブロック以内に20秒滞在すると安全ワールドへ帰還
 * - 指名手配中は帰還不可
 */
public class SpawnBeaconManager {

    /** 帰還に必要な待機秒数 */
    private static final int REQUIRED_SECONDS = 20;
    /** 帰還可能な半径（ブロック） */
    private static final double RETURN_RADIUS = 10.0;
    /** パーティクルを送信する半径（ブロック） */
    private static final double PARTICLE_SEND_RADIUS = 300.0;
    /** パーティクル柱の高さ（ブロック） */
    private static final int PILLAR_HEIGHT = 60;

    private final JavaPlugin plugin;
    private final WorldManager worldManager;
    private final WantedManager wantedManager;
    private final String pvpWorldName;

    /** プレイヤーがスポーン付近に居続けた秒数 */
    private final Map<UUID, Integer> waitSeconds = new HashMap<>();

    public SpawnBeaconManager(JavaPlugin plugin, WorldManager worldManager,
                               WantedManager wantedManager, String pvpWorldName) {
        this.plugin       = plugin;
        this.worldManager = worldManager;
        this.wantedManager = wantedManager;
        this.pvpWorldName  = pvpWorldName;
    }

    /** タスクを開始する */
    public void start() {
        // 1秒ごとに実行
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        World pvpWorld = Bukkit.getWorld(pvpWorldName);
        if (pvpWorld == null) return;

        Location spawnLoc = pvpWorld.getSpawnLocation();

        // ─── ビーコン柱パーティクル ───────────────────────────────────────
        spawnBeaconPillar(pvpWorld, spawnLoc);

        // ─── 帰還判定 ─────────────────────────────────────────────────────
        for (Player player : pvpWorld.getPlayers()) {
            double dist = player.getLocation().distance(spawnLoc);

            if (dist > RETURN_RADIUS) {
                // 圏外: カウントリセット
                if (waitSeconds.containsKey(player.getUniqueId())) {
                    waitSeconds.remove(player.getUniqueId());
                    sendActionBar(player, ChatColor.RED + "帰還がキャンセルされました。");
                }
                continue;
            }

            // 指名手配中は帰還不可
            if (wantedManager.isWanted(player.getUniqueId())) {
                sendActionBar(player,
                        ChatColor.RED + "⚠ 指名手配中は帰還できません！");
                waitSeconds.remove(player.getUniqueId());
                continue;
            }

            // カウントアップ
            int seconds = waitSeconds.getOrDefault(player.getUniqueId(), 0) + 1;

            if (seconds >= REQUIRED_SECONDS) {
                // 帰還！
                waitSeconds.remove(player.getUniqueId());
                sendActionBar(player, ChatColor.GREEN + "✦ 安全ワールドへ帰還します！");
                worldManager.returnToSafeWorld(player);
                // 帰還エフェクト
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            } else {
                waitSeconds.put(player.getUniqueId(), seconds);
                int remaining = REQUIRED_SECONDS - seconds;
                sendActionBar(player,
                        ChatColor.AQUA + "✦ 帰還まで: " + ChatColor.WHITE + remaining + "秒"
                        + ChatColor.GRAY + " │ 動かないでください");
            }
        }
    }

    /**
     * スポーン上空にビーコン柱パーティクルを生成する。
     * 範囲内の全プレイヤーに送信。
     */
    private void spawnBeaconPillar(World world, Location spawnLoc) {
        double bx = spawnLoc.getX() + 0.5;
        double bz = spawnLoc.getZ() + 0.5;

        for (Player player : world.getPlayers()) {
            double pdist = player.getLocation().distance(spawnLoc);
            if (pdist > PARTICLE_SEND_RADIUS) continue;

            for (int dy = 0; dy <= PILLAR_HEIGHT; dy += 2) {
                Location pLoc = new Location(world, bx, spawnLoc.getY() + dy, bz);
                // 外側のリング: END_ROD（白くふわふわ漂う、遠くから視認しやすい）
                player.spawnParticle(Particle.END_ROD, pLoc, 1, 0.3, 0, 0.3, 0.02);
                // 中心の芯: FLAME（黄橙色）
                if (dy % 4 == 0) {
                    player.spawnParticle(Particle.FLAME, pLoc, 1, 0.1, 0, 0.1, 0.01);
                }
            }
            // 地面付近に大きなリング（分かりやすいマーカー）
            spawnGroundRing(player, world, bx, spawnLoc.getY(), bz);
        }
    }

    /** スポーン地上付近に半径10の帰還ゾーンリングを描画する */
    private void spawnGroundRing(Player player, World world, double cx, double baseY, double cz) {
        double y = baseY + 0.1;
        int steps = 36; // 360° / 10°
        for (int i = 0; i < steps; i++) {
            double angle = Math.toRadians(i * (360.0 / steps));
            double rx = cx + RETURN_RADIUS * Math.cos(angle);
            double rz = cz + RETURN_RADIUS * Math.sin(angle);
            Location rLoc = new Location(world, rx, y, rz);
            player.spawnParticle(Particle.VILLAGER_HAPPY, rLoc, 1, 0, 0, 0, 0);
        }
    }

    private static void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(text));
    }
}
