package lobby.one_day_keizai.manager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * オーバーワールド帰還スポットシステム。
 *
 * ワールドスポーンを中心に 150 ブロック間隔の 3×3 グリッド（計9点）に
 * 帰還ビーコンを配置。各ビーコン点から10ブロック以内で20秒待機すると帰還。
 * 指名手配中は帰還不可。
 *
 * グリッド配置（スポーン中心、各 GRID_STEP=300 ブロック）:
 *   (-300,-300) (0,-300) (300,-300)
 *   (-300,  0 ) (0,  0 ) (300,  0 )  ← (0,0)がスポーン
 *   (-300, 300) (0, 300) (300, 300)
 */
public class SpawnBeaconManager {

    /** 帰還に必要な待機秒数 */
    private static final int REQUIRED_SECONDS = 20;
    /** 帰還可能な半径（ブロック） */
    private static final double RETURN_RADIUS = 10.0;
    /** パーティクルを表示するビーコン点からの最大距離 */
    private static final double PARTICLE_SEND_RADIUS = 200.0;
    /** パーティクル柱の高さ（ブロック） */
    private static final int PILLAR_HEIGHT = 60;
    /** グリッド間隔（ブロック） */
    private static final int GRID_STEP = 300;
    /** グリッド半径（-1, 0, 1 の3点 × XZ） */
    private static final int GRID_RANGE = 1;

    private final JavaPlugin plugin;
    private final WorldManager worldManager;
    private final WantedManager wantedManager;
    private final String pvpWorldName;

    /** キャッシュされたビーコン点リスト */
    private List<Location> beaconPoints = null;
    /** プレイヤーがどのビーコン付近に居続けた秒数 */
    private final Map<UUID, Integer> waitSeconds = new HashMap<>();

    public SpawnBeaconManager(JavaPlugin plugin, WorldManager worldManager,
                               WantedManager wantedManager, String pvpWorldName) {
        this.plugin        = plugin;
        this.worldManager  = worldManager;
        this.wantedManager = wantedManager;
        this.pvpWorldName  = pvpWorldName;
    }

    /** タスクを開始する */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        World pvpWorld = Bukkit.getWorld(pvpWorldName);
        if (pvpWorld == null) return;

        // ビーコン点を初期化（初回のみ）
        if (beaconPoints == null) {
            beaconPoints = buildBeaconPoints(pvpWorld.getSpawnLocation());
        }

        // ─── ビーコン柱パーティクル ───────────────────────────────────────
        for (Location bp : beaconPoints) {
            spawnBeaconPillar(pvpWorld, bp);
        }

        // ─── 帰還判定 ─────────────────────────────────────────────────────
        for (Player player : pvpWorld.getPlayers()) {
            Location nearest = nearestBeacon(player.getLocation());
            if (nearest == null) continue;

            double dist = player.getLocation().distance(nearest);

            if (dist > RETURN_RADIUS) {
                if (waitSeconds.containsKey(player.getUniqueId())) {
                    waitSeconds.remove(player.getUniqueId());
                    sendActionBar(player, ChatColor.RED + "帰還がキャンセルされました。");
                }
                continue;
            }

            // 指名手配中は帰還不可
            if (wantedManager.isWanted(player.getUniqueId())) {
                sendActionBar(player, ChatColor.RED + "⚠ 指名手配中は帰還できません！");
                waitSeconds.remove(player.getUniqueId());
                continue;
            }

            // カウントアップ
            int seconds = waitSeconds.getOrDefault(player.getUniqueId(), 0) + 1;

            if (seconds >= REQUIRED_SECONDS) {
                waitSeconds.remove(player.getUniqueId());
                sendActionBar(player, ChatColor.GREEN + "✦ 安全ワールドへ帰還します！");
                worldManager.returnToSafeWorld(player);
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
     * ワールドスポーンを中心に 3×3 グリッドの帰還点を生成する。
     * Y座標はスポーンの地表Y（世界最高ソリッドブロックの1つ上）を使う。
     */
    private List<Location> buildBeaconPoints(Location spawnLoc) {
        World world = spawnLoc.getWorld();
        List<Location> points = new ArrayList<>();

        for (int dx = -GRID_RANGE; dx <= GRID_RANGE; dx++) {
            for (int dz = -GRID_RANGE; dz <= GRID_RANGE; dz++) {
                double x = spawnLoc.getX() + dx * GRID_STEP;
                double z = spawnLoc.getZ() + dz * GRID_STEP;
                // 地表Yを取得（チャンクが未ロードの場合はspawnのYを流用）
                int groundY = world.getHighestBlockYAt((int) x, (int) z);
                if (groundY < 0) groundY = spawnLoc.getBlockY();
                points.add(new Location(world, x, groundY + 1, z));
            }
        }
        return points;
    }

    /** 最寄りのビーコン点を返す */
    private Location nearestBeacon(Location loc) {
        if (beaconPoints == null || beaconPoints.isEmpty()) return null;
        Location nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Location bp : beaconPoints) {
            double d = loc.distance(bp);
            if (d < minDist) {
                minDist = d;
                nearest = bp;
            }
        }
        return nearest;
    }

    /** ビーコン点上空に柱パーティクルを生成する */
    private void spawnBeaconPillar(World world, Location bpLoc) {
        double bx = bpLoc.getX();
        double bz = bpLoc.getZ();
        double baseY = bpLoc.getY();

        for (Player player : world.getPlayers()) {
            // プレイヤーがこのビーコン点から離れすぎている場合はスキップ
            double pdist = player.getLocation().distance(bpLoc);
            if (pdist > PARTICLE_SEND_RADIUS) continue;

            for (int dy = 0; dy <= PILLAR_HEIGHT; dy += 2) {
                Location pLoc = new Location(world, bx, baseY + dy, bz);
                player.spawnParticle(Particle.END_ROD, pLoc, 1, 0.3, 0, 0.3, 0.02);
                if (dy % 4 == 0) {
                    player.spawnParticle(Particle.FLAME, pLoc, 1, 0.1, 0, 0.1, 0.01);
                }
            }
            // 帰還ゾーンのリング表示
            spawnGroundRing(player, world, bx, baseY, bz);
        }
    }

    /** 帰還ゾーン（半径10）の地面リングを描画する */
    private void spawnGroundRing(Player player, World world, double cx, double baseY, double cz) {
        double y = baseY + 0.1;
        int steps = 36;
        for (int i = 0; i < steps; i++) {
            double angle = Math.toRadians(i * (360.0 / steps));
            double rx = cx + RETURN_RADIUS * Math.cos(angle);
            double rz = cz + RETURN_RADIUS * Math.sin(angle);
            player.spawnParticle(Particle.VILLAGER_HAPPY,
                    new Location(world, rx, y, rz), 1, 0, 0, 0, 0);
        }
    }

    /**
     * 9点の帰還ビーコンからランダムに1点を選び、
     * 上空 skyOffset ブロック上の入場位置を返す。
     * ビーコン点がまだ初期化されていない場合はその場で計算する。
     */
    public Location getRandomEntryPoint(World world, int skyOffset) {
        List<Location> pts = beaconPoints;
        if (pts == null || pts.isEmpty()) {
            pts = buildBeaconPoints(world.getSpawnLocation());
            // 初回入場時にキャッシュしておく
            beaconPoints = pts;
        }
        Location base = pts.get(new Random().nextInt(pts.size()));
        return new Location(world, base.getX(), base.getY() + skyOffset, base.getZ());
    }

    private static void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }
}
