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
 * config keys:
 *   compass-shop-front-world/x/y/z  … コンパスが反応する地点（入口）
 *   compass-shop-front-radius       … front の有効半径（デフォルト15）
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

    /** コンパスが反応する入口地点（null=どこでも反応） */
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

    /** /compass setshop front — コンパスが反応する入口地点を設定 */
    public void setShopFront(Location loc) {
        this.shopFront = loc.clone();
        saveLocation("compass-shop-front", loc);
    }

    /** /compass setshop back — TP先（ショップ内）を設定 */
    public void setShopBack(Location loc) {
        this.shopBack = loc.clone();
        saveLocation("compass-shop-back", loc);
    }

    public Location getShopFront() { return shopFront; }
    public Location getShopBack()  { return shopBack;  }

    // ─── ショップコンパス右クリック処理 ─────────────────────────

    /**
     * ショップコンパス右クリック処理。
     *  1. front が設定済みなら、そこから compass-shop-front-radius 内にいるか確認
     *  2. 許可職業チェック
     *  3. back へTP
     * @return true=TP成功, false=弾いた
     */
    public boolean handleShopCompassClick(Player player, JobManager jobManager) {
        // ─ front 範囲チェック ─
        if (shopFront != null) {
            if (!Objects.equals(shopFront.getWorld(), player.getWorld())) {
                player.sendMessage(ChatColor.RED + "このコンパスは " +
                        shopFront.getWorld().getName() + " ワールドで使用してください。");
                return false;
            }
            double radius = plugin.getConfig().getDouble("compass-shop-front-radius", 15.0);
            if (player.getLocation().distanceSquared(shopFront) > radius * radius) {
                player.sendMessage(ChatColor.RED + "ショップ入口付近でコンパスを使用してください！");
                return false;
            }
        }

        // ─ back 未設定チェック ─
        if (shopBack == null) {
            player.sendMessage(ChatColor.RED + "ショップのTP先が未設定です。OPに問い合わせてください。");
            return false;
        }

        // ─ 職業チェック ─
        Job job = jobManager.getJob(player.getUniqueId());
        List<String> allowedNames = plugin.getConfig().getStringList("compass-shop-allowed-jobs");
        if (allowedNames.isEmpty()) {
            allowedNames = List.of("MERCHANT", "WEALTHY_MERCHANT");
        }
        boolean allowed = player.isOp() ||
                allowedNames.stream().anyMatch(name -> name.equalsIgnoreCase(job.name()));
        if (!allowed) {
            player.sendMessage(ChatColor.RED + "このショップは商人職業専用です！");
            player.sendMessage(ChatColor.GOLD + "職業を変更するには " +
                    ChatColor.WHITE + "/job select" + ChatColor.GOLD + " を使ってください。");
            return false;
        }

        // ─ TP ─
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
            ChatColor.GRAY + "入口付近で右クリック → ショップへTP",
            ChatColor.GRAY + "economy ワールドで使用"
        ));
        meta.getPersistentDataContainer().set(compassTypeKey, PersistentDataType.STRING, TYPE_SHOP);
        item.setItemMeta(meta);
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
        ItemStack held = player.getInventory().getItemInMainHand();
        String type = getCompassType(held);
        if (type == null) return;

        switch (type) {
            case TYPE_SHOP   -> updateShopCompass(player);
            case TYPE_WANTED -> updateWantedCompass(player);
        }
    }

    // ─── ショップコンパス更新（コンパス針をbackへ向ける）──────────

    private void updateShopCompass(Player player) {
        // コンパスはback（ショップ内）を指す。frontに向けてもよいが遠い方が自然
        Location target = shopBack != null ? shopBack : shopFront;
        if (target == null) return;
        if (!Objects.equals(target.getWorld(), player.getWorld())) return;
        player.setCompassTarget(target);
    }

    // ─── 賞金首コンパス更新 ──────────────────────────────────────

    private void updateWantedCompass(Player player) {
        Location nearest = null;
        double minDist = Double.MAX_VALUE;
        Location pLoc = player.getLocation();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (!wantedManager.isWanted(target.getUniqueId())) continue;
            if (!Objects.equals(target.getWorld(), player.getWorld())) continue;

            double d = pLoc.distanceSquared(target.getLocation());
            if (d < minDist) { minDist = d; nearest = target.getLocation(); }
        }

        if (nearest != null) {
            player.setCompassTarget(nearest);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getItemMeta() instanceof CompassMeta cm) {
                cm.setLodestone(nearest);
                cm.setLodestoneTracked(false);
                held.setItemMeta(cm);
            }
        } else {
            player.setCompassTarget(player.getLocation());
        }
    }
}
