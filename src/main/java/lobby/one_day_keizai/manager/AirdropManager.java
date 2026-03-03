package lobby.one_day_keizai.manager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * エアドロップシステム。
 * - 定期的にPvPワールドのランダム座標にクレート（チェスト）を落下させる
 * - クレートを確保したプレイヤーは脱出するまで発光（Glowing）が付与される
 * - 脱出（安全ワールドへの移動）で発光が解除される
 */
public class AirdropManager {

    private final JavaPlugin plugin;
    private final WorldManager worldManager;

    /** Location丸め比較のため、ブロック座標でキー管理 */
    private final Map<String, Location> activeCrates = new HashMap<>();
    /** クレートを確保中のプレイヤーUUID（脱出で解除） */
    private final Set<UUID> securedPlayers = new HashSet<>();
    /** クレート毎の花火タスク */
    private final Map<String, BukkitTask> fireworkTasks = new HashMap<>();

    private final int intervalTicks;
    private final int warningTicks;
    private final int spawnRange;
    private final List<ItemStack> crateItems;

    public AirdropManager(JavaPlugin plugin, WorldManager worldManager,
                          int intervalMinutes, int warningSeconds, int spawnRange,
                          List<ItemStack> crateItems) {
        this.plugin       = plugin;
        this.worldManager = worldManager;
        this.intervalTicks = intervalMinutes * 60 * 20;
        this.warningTicks  = warningSeconds * 20;
        this.spawnRange    = spawnRange;
        this.crateItems    = crateItems.isEmpty() ? defaultItems() : crateItems;
    }

    // ─── スケジューラー ────────────────────────────────────────────

    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::scheduleDrop, intervalTicks, intervalTicks);
    }

    private void scheduleDrop() {
        // 予告アナウンス
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD
                + "【エアドロップ】" + ChatColor.RESET + ChatColor.YELLOW
                + " まもなく補給物資が投下されます！準備してください！");

        // warningTicks 後に実際にドロップ
        Bukkit.getScheduler().runTaskLater(plugin, this::executeDrop, warningTicks);
    }

    private void executeDrop() {
        World pvpWorld = Bukkit.getWorld(worldManager.getOverworldName());
        if (pvpWorld == null) return;

        Location spawn = pvpWorld.getSpawnLocation();
        Random rng = new Random();

        // ランダム座標を決定（スポーンから50〜spawnRangeブロック）
        double angle    = rng.nextDouble() * 2 * Math.PI;
        double distance = 50 + rng.nextDouble() * (spawnRange - 50);
        int x = (int)(spawn.getX() + Math.cos(angle) * distance);
        int z = (int)(spawn.getZ() + Math.sin(angle) * distance);
        int y = pvpWorld.getHighestBlockYAt(x, z) + 1;

        Location loc = new Location(pvpWorld, x, y, z);
        dropCrate(loc);
    }

    // ─── クレート設置 ─────────────────────────────────────────────

    public void dropCrate(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // チェストブロックを設置
        Block block = loc.getBlock();
        block.setType(Material.CHEST);

        // チェストにアイテムを格納
        if (block.getState() instanceof Chest chest) {
            for (int i = 0; i < Math.min(crateItems.size(), chest.getInventory().getSize()); i++) {
                chest.getInventory().setItem(i, crateItems.get(i).clone());
            }
        }

        String key = locationKey(loc);
        activeCrates.put(key, loc.clone());

        // アナウンス
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "╔══════════════════════════╗");
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.RED + ChatColor.BOLD
                + "★ エアドロップ投下！ ★");
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.WHITE + "座標: "
                + ChatColor.AQUA + "X:" + loc.getBlockX()
                + " Y:" + loc.getBlockY()
                + " Z:" + loc.getBlockZ());
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.YELLOW
                + "クレートを確保すると発光します！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "╚══════════════════════════╝");
        Bukkit.broadcastMessage("");

        // 着地エフェクト
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc.clone().add(0.5, 1, 0.5), 5);

        // 花火を30秒ごとに打ち上げて位置を知らせる
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!activeCrates.containsKey(key)) return;
            Location center = loc.clone().add(0.5, 0, 0.5);
            spawnSignalFirework(center);
        }, 0L, 600L); // 30秒ごと

        fireworkTasks.put(key, task);

        // 10分経過で消滅
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCrate(key), 20L * 60 * 10);
    }

    // ─── クレート確保 ─────────────────────────────────────────────

    /**
     * プレイヤーがチェストブロックを開いた時に呼ぶ。
     * @return アイドロップクレートだったら true
     */
    public boolean trySecure(Player player, Location loc) {
        String key = locationKey(loc);
        if (!activeCrates.containsKey(key)) return false;

        activeCrates.remove(key);
        stopFirework(key);

        // アイテム付与
        for (ItemStack item : crateItems) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
        }

        // チェストを破壊
        loc.getBlock().setType(Material.AIR);

        // 発光付与（脱出するまで継続）
        securedPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                Integer.MAX_VALUE, 0, false, false, true));

        // アナウンス
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD
                + "【エアドロップ確保】" + ChatColor.RESET + ChatColor.WHITE
                + " " + player.getName() + " がクレートを確保しました！"
                + ChatColor.YELLOW + " 発光中 — 脱出を阻止せよ！");

        // 効果音
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        player.sendMessage(ChatColor.GOLD + "クレートを確保しました！安全ワールドに戻るまで発光が続きます。");
        return true;
    }

    /** 安全ワールドに移動した時に発光を解除する */
    public void onPlayerEscape(Player player) {
        if (!securedPlayers.remove(player.getUniqueId())) return;
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.sendMessage(ChatColor.GREEN + "脱出成功！発光が解除されました。");
        Bukkit.broadcastMessage(ChatColor.GREEN + "【エアドロップ】"
                + ChatColor.WHITE + " " + player.getName() + " が脱出しました！");
    }

    /** プレイヤーが死亡した時も発光を解除する */
    public void onPlayerDeath(Player player) {
        if (!securedPlayers.remove(player.getUniqueId())) return;
        player.removePotionEffect(PotionEffectType.GLOWING);
        Bukkit.broadcastMessage(ChatColor.GRAY + "【エアドロップ】"
                + ChatColor.WHITE + " " + player.getName() + " が撃破され、クレートの発光が解除されました。");
    }

    // ─── 内部ユーティリティ ───────────────────────────────────────

    private void expireCrate(String key) {
        Location loc = activeCrates.remove(key);
        stopFirework(key);
        if (loc != null) {
            loc.getBlock().setType(Material.AIR);
            Bukkit.broadcastMessage(ChatColor.GRAY + "【エアドロップ】誰も確保しないままクレートが消滅しました。");
        }
    }

    private void stopFirework(String key) {
        BukkitTask t = fireworkTasks.remove(key);
        if (t != null) t.cancel();
    }

    private void spawnSignalFirework(Location loc) {
        try {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.RED, Color.ORANGE)
                    .withFade(Color.YELLOW)
                    .flicker(true)
                    .build());
            meta.setPower(2);
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static List<ItemStack> defaultItems() {
        return List.of(
                new ItemStack(Material.DIAMOND, 5),
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.GOLDEN_APPLE, 8),
                new ItemStack(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.COOKED_BEEF, 32),
                new ItemStack(Material.OBSIDIAN, 16)
        );
    }

    public boolean isSecured(UUID uuid) {
        return securedPlayers.contains(uuid);
    }

    public int getActiveCrateCount() {
        return activeCrates.size();
    }
}
