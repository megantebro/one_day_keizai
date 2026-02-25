package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 豪商（WEALTHY_MERCHANT）がロバのチェストを開くと、通常の15スロットではなく
 * 45スロットの仮想インベントリを表示する。
 * <p>
 * 拡張データはロバエンティティの PersistentDataContainer に保存されるため、
 * サーバー再起動後も保持される（ワールドが保存されている限り）。
 * <p>
 * ※ 豪商の仮想インベントリとロバの実際のチェストスロットは独立した空間。
 * 他の職業は通常の15スロットチェストを使用できる。
 */
public class DonkeyChestListener implements Listener {

    private static final int EXTENDED_SIZE = 45;
    private static final String PDC_KEY = "donkey_extended_inventory";

    private final JobManager jobManager;
    private final JavaPlugin plugin;
    private final NamespacedKey pdcKey;

    /** 豪商が仮想インベントリを開いている間、プレイヤーUUID → ロバ のマッピングを保持 */
    private final Map<UUID, ChestedHorse> openingDonkeys = new HashMap<>();

    public DonkeyChestListener(JavaPlugin plugin, JobManager jobManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.pdcKey = new NamespacedKey(plugin, PDC_KEY);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // インベントリを開く
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // ChestedHorse (ロバ・ラマ・ラバ) かつチェスト装着済みのみ対象
        if (!(event.getInventory().getHolder() instanceof ChestedHorse horse)) return;
        if (!horse.isCarryingChest()) return;

        // 豪商のみ拡張インベントリを使用
        if (!jobManager.isWealthyMerchant(player.getUniqueId())) return;

        // バニラのインベントリを開かせない
        event.setCancelled(true);

        // ─ 次 tick で仮想インベントリを開く (setCancelled後の安全措置) ─
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            String title = ChatColor.DARK_AQUA + "豪商の荷車 "
                    + ChatColor.GRAY + "[" + EXTENDED_SIZE + "スロット]";
            Inventory customInv = Bukkit.createInventory(null, EXTENDED_SIZE, title);

            ItemStack[] storedItems = loadItems(horse);
            customInv.setContents(storedItems);

            openingDonkeys.put(player.getUniqueId(), horse);
            player.openInventory(customInv);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // インベントリを閉じる
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        ChestedHorse horse = openingDonkeys.remove(player.getUniqueId());
        if (horse == null || !horse.isValid()) return;

        // 閉じたインベントリの内容をロバの PDC に保存
        saveItems(horse, event.getInventory().getContents());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // プレイヤー退出時のクリーンアップ
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // InventoryCloseEvent が先に発火するが念のためクリーンアップ
        openingDonkeys.remove(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDC 読み書き
    // ─────────────────────────────────────────────────────────────────────────

    private ItemStack[] loadItems(ChestedHorse horse) {
        PersistentDataContainer pdc = horse.getPersistentDataContainer();
        byte[] data = pdc.get(pdcKey, PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length == 0) {
            return new ItemStack[EXTENDED_SIZE];
        }
        return deserializeItems(data);
    }

    private void saveItems(ChestedHorse horse, ItemStack[] items) {
        byte[] data = serializeItems(items);
        if (data.length == 0) return;
        PersistentDataContainer pdc = horse.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE_ARRAY, data);
        // エンティティの変更を永続化するため force-update
        horse.getWorld().save();
    }

    private byte[] serializeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) {
                oos.writeObject(item);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            plugin.getLogger().warning("DonkeyChest: インベントリのシリアライズに失敗: " + e.getMessage());
            return new byte[0];
        }
    }

    private ItemStack[] deserializeItems(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[Math.max(len, EXTENDED_SIZE)];
            for (int i = 0; i < len; i++) {
                items[i] = (ItemStack) ois.readObject();
            }
            return items;
        } catch (Exception e) {
            plugin.getLogger().warning("DonkeyChest: インベントリのデシリアライズに失敗: " + e.getMessage());
            return new ItemStack[EXTENDED_SIZE];
        }
    }
}
