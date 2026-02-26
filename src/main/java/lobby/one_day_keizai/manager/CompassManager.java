package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.One_day_keizai;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
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
 *  - SHOP    コンパス: 最寄りショップを指す（economy ワールド）
 *  - WANTED  コンパス: 最寄り指名手配プレイヤーを指す（overworld）
 */
public class CompassManager {

    public static final String TYPE_SHOP   = "shop";
    public static final String TYPE_WANTED = "wanted";

    private final NamespacedKey compassTypeKey;
    private final JavaPlugin plugin;
    private final WantedManager wantedManager;
    private final WorldManager worldManager;

    /** config から読んだショップ座標: key=name, value=Location */
    private final Map<String, Location> shopLocations = new LinkedHashMap<>();

    public CompassManager(JavaPlugin plugin, WantedManager wantedManager,
                          WorldManager worldManager) {
        this.plugin        = plugin;
        this.wantedManager = wantedManager;
        this.worldManager  = worldManager;
        this.compassTypeKey = new NamespacedKey(plugin, "compass_type");
        loadShops();
    }

    private void loadShops() {
        shopLocations.clear();
        String worldName = plugin.getConfig().getString("shop-world", "economy");
        World world = Bukkit.getWorld(worldName);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("shops");
        if (sec == null || world == null) return;
        for (String key : sec.getKeys(false)) {
            double x = sec.getDouble(key + ".x");
            double y = sec.getDouble(key + ".y");
            double z = sec.getDouble(key + ".z");
            shopLocations.put(key, new Location(world, x + 0.5, y, z + 0.5));
        }
    }

    // ─── アイテム生成 ────────────────────────────────────────────

    public ItemStack createShopCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "ショップコンパス");
        meta.setLore(List.of(
            ChatColor.GRAY + "最寄りのショップを指し示す",
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

    // ─── ショップコンパス更新 ─────────────────────────────────────

    private void updateShopCompass(Player player) {
        if (shopLocations.isEmpty()) return;

        Location nearest = null;
        double minDist = Double.MAX_VALUE;
        Location pLoc = player.getLocation();

        for (Location loc : shopLocations.values()) {
            // 同ワールドのみ対象
            if (!Objects.equals(loc.getWorld(), player.getWorld())) continue;
            double d = pLoc.distanceSquared(loc);
            if (d < minDist) { minDist = d; nearest = loc; }
        }

        if (nearest != null) {
            player.setCompassTarget(nearest);
        }
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
            // コンパスのトラッキング先をアイテムメタに反映（ローディングポインタ表示）
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getItemMeta() instanceof CompassMeta cm) {
                cm.setLodestone(nearest);
                cm.setLodestoneTracked(false);
                held.setItemMeta(cm);
            }
        } else {
            player.setCompassTarget(player.getLocation()); // 指名手配なし → 自分を指す
        }
    }
}
