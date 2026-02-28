package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.job.Job;
import lobby.one_day_keizai.job.JobManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * カスタムコンパス管理
 *  - SHOP    コンパス: front地点付近で右クリック → back地点(ショップ内)へTP
 *  - WANTED  コンパス: 最寄り指名手配プレイヤーを指す（overworld）
 *
 * 針の向きはアイテム自体のロードストーン座標で管理（CompassMeta.setLodestone）。
 * setCompassTarget は使わない（複数人で1人しか動かない問題を回避）。
 *
 * config keys:
 *   compass-shop-front-world/x/y/z  … コンパスが反応する地点（入口）
 *   compass-shop-front-radius       … front の有効半径（デフォルト3）
 *   compass-shop-back-world/x/y/z   … TP先（ショップ内）
 *   compass-shop-allowed-jobs       … 許可職業リスト
 */
public class CompassManager {

    public static final String TYPE_SHOP   = "shop";
    public static final String TYPE_WANTED = "wanted";

    private final NamespacedKey compassTypeKey;
    private final JavaPlugin plugin;
    private final WantedManager wantedManager;
    private final WorldManager worldManager;

    /** コンパスが反応する入口地点 */
    private Location shopFront = null;
    /** TP先（ショップ内） */
    private Location shopBack  = null;

    public CompassManager(JavaPlugin plugin, WantedManager wantedManager,
                          WorldManager worldManager) {
        this.plugin         = plugin;
        this.wantedManager  = wantedManager;
        this.worldManager   = worldManager;
        this.compassTypeKey = new NamespacedKey(plugin, "compass_type");
        loadShopLocations();
    }

    // ─── 読み込み / 保存 ─────────────────────────────────────────

    private void loadShopLocations() {
        shopFront = loadLocation("compass-shop-front");
        shopBack  = loadLocation("compass-shop-back");
    }

    private Location loadLocation(String prefix) {
        String worldName = plugin.getConfig().getString(prefix + "-world", "");
        if (worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = plugin.getConfig().getDouble(prefix + "-x", 0);
        double y = plugin.getConfig().getDouble(prefix + "-y", 64);
        double z = plugin.getConfig().getDouble(prefix + "-z", 0);
        return new Location(world, x, y, z);
    }

    private void saveLocation(String prefix, Location loc) {
        plugin.getConfig().set(prefix + "-world", loc.getWorld().getName());
        plugin.getConfig().set(prefix + "-x", loc.getX());
        plugin.getConfig().set(prefix + "-y", loc.getY());
        plugin.getConfig().set(prefix + "-z", loc.getZ());
        plugin.saveConfig();
    }

    public void setShopFront(Location loc) {
        this.shopFront = loc.clone();
        saveLocation("compass-shop-front", loc);
    }

    public void setShopBack(Location loc) {
        this.shopBack = loc.clone();
        saveLocation("compass-shop-back", loc);
    }

    public Location getShopFront() { return shopFront; }
    public Location getShopBack()  { return shopBack;  }

    // ─── ロードストーン座標をアイテムに書き込むヘルパー ──────────

    /**
     * CompassMeta のロードストーン座標を更新する。
     * setLodestoneTracked(false) にすることで実際のロードストーンブロック不要。
     * アイテム自体に座標が入るため、誰が持っても同じ向きを指す。
     */
    private void applyLodestone(ItemStack item, Location target) {
        if (item == null || item.getType() != Material.COMPASS) return;
        ItemMeta raw = item.getItemMeta();
        if (!(raw instanceof CompassMeta meta)) return;
        meta.setLodestone(target);
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);
    }

    /** 指定プレイヤーのインベントリ内のコンパスをすべて更新 */
    private void applyLodestoneToAll(Player player, String compassType, Location target) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType() != Material.COMPASS) continue;
            if (!compassType.equals(getCompassType(item))) continue;
            applyLodestone(item, target);
        }
    }

    // ─── ショップコンパス右クリック処理 ─────────────────────────

    public boolean handleShopCompassClick(Player player, JobManager jobManager) {
        double radius = plugin.getConfig().getDouble("compass-shop-front-radius", 3.0);

        // ─ back 付近なら front へ帰還 ─
        if (shopBack != null
                && shopBack.getWorld() != null
                && shopBack.getWorld().getName().equals(player.getWorld().getName())
                && player.getLocation().distanceSquared(shopBack) <= radius * radius) {
            if (shopFront == null) {
                player.sendMessage(ChatColor.RED + "帰還先（front）が未設定です。OPに問い合わせてください。");
                return false;
            }
            player.teleport(shopFront);
            player.sendMessage(ChatColor.YELLOW + "ショップから戻りました。");
            return true;
        }

        // ─ front 範囲チェック ─
        if (shopFront != null) {
            if (shopFront.getWorld() == null
                    || !shopFront.getWorld().getName().equals(player.getWorld().getName())) {
                player.sendMessage(ChatColor.RED + "このコンパスは " +
                        (shopFront.getWorld() != null ? shopFront.getWorld().getName() : "?") +
                        " ワールドで使用してください。");
                return false;
            }
            if (player.getLocation().distanceSquared(shopFront) > radius * radius) {
                player.sendMessage(ChatColor.RED + "ショップ入口付近でコンパスを使用してください！");
                return false;
            }
        }

        if (shopBack == null) {
            player.sendMessage(ChatColor.RED + "ショップのTP先が未設定です。OPに問い合わせてください。");
            return false;
        }

        // ─ 職業チェック ─
        Job job = jobManager.getJob(player.getUniqueId());
        List<String> allowedNames = plugin.getConfig().getStringList("compass-shop-allowed-jobs");
        if (allowedNames.isEmpty()) allowedNames = List.of("MERCHANT", "WEALTHY_MERCHANT");
        boolean allowed = player.isOp() ||
                allowedNames.stream().anyMatch(name -> name.equalsIgnoreCase(job.name()));
        if (!allowed) {
            player.sendMessage(ChatColor.RED + "このショップは商人職業専用です！");
            player.sendMessage(ChatColor.GOLD + "職業を変更するには " +
                    ChatColor.WHITE + "/job select" + ChatColor.GOLD + " を使ってください。");
            return false;
        }

        player.teleport(shopBack);
        player.sendMessage(ChatColor.AQUA + "ショップへようこそ！");
        return true;
    }

    // ─── アイテム生成 ────────────────────────────────────────────

    public ItemStack createShopCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "ショップコンパス");
        meta.setLore(List.of(
            ChatColor.YELLOW + "右クリックでコンパスを更新・ショップへTP",
            ChatColor.GRAY + "メインハンドでのみ使用可能",
            ChatColor.GRAY + "overworld: 入口方向を指す",
            ChatColor.GRAY + "economy: 出口方向を指す"
        ));
        meta.getPersistentDataContainer().set(compassTypeKey, PersistentDataType.STRING, TYPE_SHOP);
        item.setItemMeta(meta);
        // 初期ロードストーン設定（作成時点で front へ向ける）
        if (shopFront != null) applyLodestone(item, shopFront);
        return item;
    }

    public ItemStack createWantedCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "賞金首コンパス");
        meta.setLore(List.of(
            ChatColor.GRAY + "最寄りの指名手配プレイヤーを追跡",
            ChatColor.GRAY + "overworld で使用"
        ));
        meta.getPersistentDataContainer().set(compassTypeKey, PersistentDataType.STRING, TYPE_WANTED);
        item.setItemMeta(meta);
        return item;
    }

    // ─── 種類判定 ────────────────────────────────────────────────

    public String getCompassType(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(compassTypeKey, PersistentDataType.STRING);
    }

    // ─── 定期更新タスク ──────────────────────────────────────────

    public void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerCompass(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5秒毎
    }

    private void updatePlayerCompass(Player player) {
        boolean hasShop = false, hasWanted = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.COMPASS) continue;
            String type = getCompassType(item);
            if (TYPE_SHOP.equals(type))   hasShop = true;
            if (TYPE_WANTED.equals(type)) hasWanted = true;
        }
        if (hasShop)   updateShopCompass(player);
        if (hasWanted) updateWantedCompass(player);
    }

    // ─── ショップコンパス更新（ロードストーン書き換え）────────────

    public void updateShopCompass(Player player) {
        // overworld → front を指す / economy → back を指す
        Location target;
        if (worldManager.isSafeWorld(player.getWorld())) {
            target = shopBack != null ? shopBack : shopFront;
        } else {
            target = shopFront != null ? shopFront : shopBack;
        }
        if (target == null) return;
        // 異なるワールドの座標は指定不可（クロスワールドでは針が回転する）
        if (target.getWorld() == null
                || !target.getWorld().getName().equals(player.getWorld().getName())) return;

        applyLodestoneToAll(player, TYPE_SHOP, target);
    }

    // ─── 賞金首コンパス更新（ロードストーン書き換え）────────────

    private void updateWantedCompass(Player player) {
        Location nearest = null;
        double minDist = Double.MAX_VALUE;
        Location pLoc = player.getLocation();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (!wantedManager.isWanted(target.getUniqueId())) continue;
            if (target.getWorld() == null
                    || !target.getWorld().getName().equals(player.getWorld().getName())) continue;

            double d = pLoc.distanceSquared(target.getLocation());
            if (d < minDist) { minDist = d; nearest = target.getLocation().clone(); }
        }

        if (nearest == null) {
            // 同ワールドに賞金首がいない → スポーン地点を指すようにリセット
            // （別ワールドのロードストーン座標が残ると針が回転するため）
            Location spawn = player.getWorld().getSpawnLocation();
            applyLodestoneToAll(player, TYPE_WANTED, spawn);
            return;
        }
        applyLodestoneToAll(player, TYPE_WANTED, nearest);
    }
}
