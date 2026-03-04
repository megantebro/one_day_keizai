package lobby.one_day_keizai.manager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * エアドロップシステム。
 * - 定期的にPvPワールドのランダム座標にクレート（チェスト）を落下させる
 * - 開錠には「エアドロップの鍵」が必要
 * - 開錠に5分かかる（BossBarでカウントダウン）
 * - 開錠中にチェストから離れるか死亡するとキャンセル
 * - 開錠成功後、安全ワールドへ脱出するまで発光が続く
 */
public class AirdropManager {

    /** 開錠にかかる時間（tick） = 5分 */
    private static final int OPEN_TICKS = 6000;
    /** チェストから離れられる最大距離（ブロック） */
    private static final int MAX_DISTANCE = 5;

    private final JavaPlugin plugin;
    private final WorldManager worldManager;
    private final NamespacedKey airdropKeyTag;

    /** アクティブなクレートの場所 (locationKey -> Location) */
    private final Map<String, Location> activeCrates = new HashMap<>();
    /** 着地5分後に誰でも開けるようになったクレート */
    private final Set<String> freeAccessCrates = new HashSet<>();
    /** クレートを確保済みで脱出前のプレイヤー */
    private final Set<UUID> securedPlayers = new HashSet<>();
    /** クレート位置表示用の花火タスク */
    private final Map<String, BukkitTask> fireworkTasks = new HashMap<>();
    /** 現在開錠中のセッション (locationKey -> OpenSession) */
    private final Map<String, OpenSession> openingSessions = new HashMap<>();

    private final int intervalTicks;
    private final int warningTicks;
    private final int spawnRange;
    private final List<ItemStack> crateItems;

    public AirdropManager(JavaPlugin plugin, WorldManager worldManager,
                          int intervalMinutes, int warningSeconds, int spawnRange,
                          List<ItemStack> crateItems) {
        this.plugin        = plugin;
        this.worldManager  = worldManager;
        this.airdropKeyTag = new NamespacedKey(plugin, "airdrop_key");
        this.intervalTicks = intervalMinutes * 60 * 20;
        this.warningTicks  = warningSeconds * 20;
        this.spawnRange    = spawnRange;
        this.crateItems    = crateItems.isEmpty() ? defaultItems() : crateItems;
    }

    // ─── 鍵アイテム ──────────────────────────────────────────────────────────

    /** エアドロップの鍵アイテムを生成する */
    public ItemStack createKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "エアドロップの鍵");
        meta.setLore(List.of(
                ChatColor.GRAY + "エアドロップクレートを開錠できる",
                ChatColor.YELLOW + "⚠ 開錠には5分かかる",
                ChatColor.GRAY + "チェストから離れると開錠がキャンセルされる"
        ));
        meta.getPersistentDataContainer().set(airdropKeyTag, PersistentDataType.BYTE, (byte) 1);
        key.setItemMeta(meta);
        return key;
    }

    /** アイテムがエアドロップの鍵かどうかを判定する */
    public boolean isAirdropKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(airdropKeyTag, PersistentDataType.BYTE);
    }

    // ─── スケジューラー ────────────────────────────────────────────────────

    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::scheduleDrop, intervalTicks, intervalTicks);
    }

    private void scheduleDrop() {
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD
                + "【エアドロップ】" + ChatColor.RESET + ChatColor.YELLOW
                + " まもなく補給物資が投下されます！準備してください！");
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnRandom, warningTicks);
    }

    /** OP コマンドから手動でランダム座標にドロップする */
    public void spawnRandom() {
        executeDrop();
    }

    private void executeDrop() {
        World pvpWorld = Bukkit.getWorld(worldManager.getOverworldName());
        if (pvpWorld == null) return;

        Location spawn = pvpWorld.getSpawnLocation();
        Random rng = new Random();
        double angle    = rng.nextDouble() * 2 * Math.PI;
        double distance = 50 + rng.nextDouble() * (spawnRange - 50);
        int x = (int)(spawn.getX() + Math.cos(angle) * distance);
        int z = (int)(spawn.getZ() + Math.sin(angle) * distance);
        int y = pvpWorld.getHighestBlockYAt(x, z) + 1;

        dropCrate(new Location(pvpWorld, x, y, z));
    }

    // ─── クレート設置 ─────────────────────────────────────────────────────

    /** 指定座標にクレートを設置し、全体にアナウンスする */
    public void dropCrate(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Block block = loc.getBlock();
        block.setType(Material.CHEST);

        String key = locationKey(loc);
        activeCrates.put(key, loc.clone());

        // アナウンス
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "╔══════════════════════════╗");
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.RED + ChatColor.BOLD + "★ エアドロップ投下！ ★");
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.WHITE + "座標: "
                + ChatColor.AQUA + "X:" + loc.getBlockX() + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
        Bukkit.broadcastMessage(ChatColor.GOLD + "  " + ChatColor.YELLOW + "鍵を持って右クリック！開錠に5分かかる！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "╚══════════════════════════╝");
        Bukkit.broadcastMessage("");

        // 着地SE・エフェクト
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc.clone().add(0.5, 1, 0.5), 5);

        // 30秒ごとに花火を上げて位置を知らせる
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!activeCrates.containsKey(key)) return;
            spawnSignalFirework(loc.clone().add(0.5, 0, 0.5));
        }, 0L, 600L);
        fireworkTasks.put(key, task);

        // 5分経過で誰でも開けるようになる
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!activeCrates.containsKey(key)) return; // すでに開封済み・消滅済み
            freeAccessCrates.add(key);
            // 進行中の開錠セッションはキャンセル（フリーアクセスへ移行）
            if (openingSessions.containsKey(key)) {
                cancelOpening(key, "クレートがフリーアクセスになりました。誰でも開けます！");
            }
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.AQUA + "【エアドロップ】" + ChatColor.WHITE
                    + " クレートが " + ChatColor.YELLOW + ChatColor.BOLD + "フリーアクセス"
                    + ChatColor.RESET + ChatColor.WHITE + " になりました！"
                    + ChatColor.GRAY + " 鍵不要・誰でも開けます（残り5分）");
            Bukkit.broadcastMessage(ChatColor.AQUA + "  座標: X:" + loc.getBlockX()
                    + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
            Bukkit.broadcastMessage("");
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }, 20L * 60 * 5);

        // 10分経過で消滅
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCrate(key), 20L * 60 * 10);
    }

    // ─── 開錠ロジック ────────────────────────────────────────────────────

    /**
     * プレイヤーがクレートを右クリックした時に呼ぶ。
     * @param player 右クリックしたプレイヤー
     * @param loc    クリックされたブロックの座標
     * @param handItem 手持ちアイテム（鍵かどうか確認する）
     * @return このブロックがエアドロップクレートだった場合 true（鍵の有無に関わらず）
     */
    public boolean tryStartOpening(Player player, Location loc, ItemStack handItem) {
        String crateKey = locationKey(loc);
        if (!activeCrates.containsKey(crateKey)) return false;

        // ── フリーアクセス（着地5分後）: 鍵不要・即開封 ──
        if (freeAccessCrates.contains(crateKey)) {
            Location crateLoc = activeCrates.get(crateKey);
            completeOpening(crateKey, crateLoc, player, null);
            return true;
        }

        // 鍵を持っていない
        if (!isAirdropKey(handItem)) {
            player.sendMessage(ChatColor.RED + "このチェストを開けるには "
                    + ChatColor.GOLD + "エアドロップの鍵" + ChatColor.RED + " が必要です。");
            return true;
        }

        // すでに誰かが開錠中
        if (openingSessions.containsKey(crateKey)) {
            OpenSession existing = openingSessions.get(crateKey);
            if (existing.player.equals(player)) {
                player.sendMessage(ChatColor.YELLOW + "すでに開錠中です...");
            } else {
                player.sendMessage(ChatColor.RED + existing.player.getName() + " がすでに開錠中です！妨害しよう！");
            }
            return true;
        }

        // このプレイヤーが別のクレートを開錠中
        for (OpenSession s : openingSessions.values()) {
            if (s.player.equals(player)) {
                player.sendMessage(ChatColor.RED + "すでに別のクレートを開錠中です。");
                return true;
            }
        }

        // 鍵を消費
        handItem.setAmount(handItem.getAmount() - 1);

        // BossBar 作成
        BossBar bar = Bukkit.createBossBar(
                ChatColor.GOLD + "開錠中... 5:00",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );
        bar.addPlayer(player);
        bar.setProgress(1.0);

        OpenSession session = new OpenSession(player, crateKey, bar);
        openingSessions.put(crateKey, session);

        // アナウンス
        player.sendMessage(ChatColor.GOLD + "開錠を開始しました。チェストから "
                + MAX_DISTANCE + " ブロック以内にいてください。（5分）");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "【エアドロップ】" + ChatColor.WHITE
                + " " + player.getName() + " がクレートの開錠を開始！妨害するなら今だ！");

        // カウントダウンタスク起動
        Location crateLoc = activeCrates.get(crateKey);
        BukkitTask[] taskRef = {null};
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            session.ticksLeft--;

            // プレイヤーのオンライン確認
            if (!player.isOnline()) {
                cancelOpening(crateKey, null);
                return;
            }

            // ワールド離脱チェック
            if (player.getWorld() == null
                    || !player.getWorld().getName().equals(worldManager.getOverworldName())) {
                cancelOpening(crateKey, "ワールドを離れたため、開錠がキャンセルされました。");
                return;
            }

            // 距離チェック
            if (player.getLocation().distanceSquared(crateLoc)
                    > (double) MAX_DISTANCE * MAX_DISTANCE) {
                cancelOpening(crateKey, "チェストから離れすぎたため、開錠がキャンセルされました。");
                return;
            }

            // BossBar 更新
            double progress = (double) session.ticksLeft / OPEN_TICKS;
            bar.setProgress(Math.max(0.0, progress));
            int secondsLeft = session.ticksLeft / 20;
            int min = secondsLeft / 60;
            int sec = secondsLeft % 60;
            bar.setTitle(ChatColor.GOLD + "開錠中... " + min + ":" + String.format("%02d", sec));

            // 中間アナウンス（2分30秒時点）
            if (session.ticksLeft == OPEN_TICKS / 2) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "【エアドロップ】" + ChatColor.WHITE
                        + " " + player.getName() + " の開錠まであと2分30秒！");
            }

            // 完了
            if (session.ticksLeft <= 0) {
                completeOpening(crateKey, crateLoc, player, bar);
            }
        }, 1L, 1L);

        session.task = taskRef[0];
        return true;
    }

    /** 開錠をキャンセルする */
    private void cancelOpening(String crateKey, String message) {
        OpenSession session = openingSessions.remove(crateKey);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        session.bar.removeAll();
        if (message != null && session.player.isOnline()) {
            session.player.sendMessage(ChatColor.RED + message);
        }
        // 鍵は既に消費済み（返却なし）
    }

    /** 開錠完了処理 */
    private void completeOpening(String crateKey, Location crateLoc, Player player, BossBar bar) {
        openingSessions.remove(crateKey);
        activeCrates.remove(crateKey);
        freeAccessCrates.remove(crateKey);
        stopFirework(crateKey);

        if (bar != null) {
            bar.setProgress(0.0);
            bar.setTitle(ChatColor.GREEN + "開錠完了！");
            // 少し遅延してから消す
            Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, 40L);
        }

        // チェスト破壊
        crateLoc.getBlock().setType(Material.AIR);

        // アイテム付与
        for (ItemStack item : crateItems) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
        }

        // 発光付与（安全ワールドへ脱出するまで）
        securedPlayers.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                Integer.MAX_VALUE, 0, false, false, true));

        player.sendMessage(ChatColor.GOLD + "✓ エアドロップクレートを開けました！安全ワールドに戻るまで発光します。");
        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD
                + "【エアドロップ獲得！】" + ChatColor.RESET + ChatColor.WHITE
                + " " + player.getName() + " がクレートを獲得！発光中 — 追え！");

        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        spawnSignalFirework(crateLoc.clone().add(0.5, 0, 0.5));
    }

    // ─── 脱出・死亡 ──────────────────────────────────────────────────────

    /** 安全ワールドへ移動した時に発光を解除する */
    public void onPlayerEscape(Player player) {
        if (!securedPlayers.remove(player.getUniqueId())) return;
        player.removePotionEffect(PotionEffectType.GLOWING);
        player.sendMessage(ChatColor.GREEN + "脱出成功！発光が解除されました。");
        Bukkit.broadcastMessage(ChatColor.GREEN + "【エアドロップ】"
                + ChatColor.WHITE + " " + player.getName() + " が脱出しました！");
    }

    /** 死亡時に開錠キャンセル＆発光解除 */
    public void onPlayerDeath(Player player) {
        // 開錠中なら取り消す
        String toCancelKey = null;
        for (Map.Entry<String, OpenSession> e : openingSessions.entrySet()) {
            if (e.getValue().player.equals(player)) {
                toCancelKey = e.getKey();
                break;
            }
        }
        if (toCancelKey != null) {
            cancelOpening(toCancelKey, "死亡したため、開錠がキャンセルされました。");
        }

        // 発光解除
        if (securedPlayers.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            Bukkit.broadcastMessage(ChatColor.GRAY + "【エアドロップ】"
                    + ChatColor.WHITE + " " + player.getName() + " が撃破されました。");
        }
    }

    // ─── 内部ユーティリティ ─────────────────────────────────────────────

    private void expireCrate(String key) {
        // 開錠中セッションもキャンセル
        if (openingSessions.containsKey(key)) {
            cancelOpening(key, "クレートが消滅したため、開錠がキャンセルされました。");
        }

        freeAccessCrates.remove(key);
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
        return loc.getWorld().getName() + ":"
                + loc.getBlockX() + ":"
                + loc.getBlockY() + ":"
                + loc.getBlockZ();
    }

    private static List<ItemStack> defaultItems() {
        List<ItemStack> items = new ArrayList<>();

        items.add(new ItemStack(Material.DIAMOND, 5));
        items.add(new ItemStack(Material.DIAMOND_BLOCK, 2));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 8));
        items.add(new ItemStack(Material.DIAMOND_CHESTPLATE));
        items.add(new ItemStack(Material.COOKED_BEEF, 32));
        items.add(new ItemStack(Material.OBSIDIAN, 16));

        // ダメージ軽減IV のエンチャント本
        items.add(enchantedBook(Enchantment.PROTECTION_ENVIRONMENTAL, 4));
        // 爆発耐性IV のエンチャント本
        items.add(enchantedBook(Enchantment.PROTECTION_EXPLOSIONS, 4));
        // 飛び道具耐性IV のエンチャント本
        items.add(enchantedBook(Enchantment.PROTECTION_PROJECTILE, 4));

        return items;
    }

    /** エンチャント本を生成する */
    private static ItemStack enchantedBook(Enchantment enchant, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta != null) {
            meta.addStoredEnchant(enchant, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    /** この座標がアクティブなクレートかどうか */
    public boolean isAirdropChest(Location loc) {
        return activeCrates.containsKey(locationKey(loc));
    }

    public boolean isSecured(UUID uuid) {
        return securedPlayers.contains(uuid);
    }

    public int getActiveCrateCount() {
        return activeCrates.size();
    }

    // ─── 内部クラス ──────────────────────────────────────────────────────

    private static class OpenSession {
        final Player player;
        final String crateKey;
        final BossBar bar;
        BukkitTask task;
        int ticksLeft = OPEN_TICKS;

        OpenSession(Player player, String crateKey, BossBar bar) {
            this.player   = player;
            this.crateKey = crateKey;
            this.bar      = bar;
        }
    }
}
