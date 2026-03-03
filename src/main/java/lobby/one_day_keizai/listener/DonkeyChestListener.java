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
import org.bukkit.event.entity.EntityDeathEvent;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 豪商（WEALTHY_MERCHANT）がロバのチェストを開くと、通常の15スロットではなく
 * 45スロットの仮想インベントリを表示する。
 * <p>
 * 拡張データはロバエンティティの PersistentDataContainer に保存される。
 * PDCはサーバーの通常自動セーブ時に永続化されるため world.save() は不要。
 */
public class DonkeyChestListener implements Listener {

    private static final int EXTENDED_SIZE = 45;
    private static final String PDC_KEY = "donkey_extended_inventory";

    private final JobManager jobManager;
    private final JavaPlugin plugin;
    private final NamespacedKey pdcKey;

    /** 豪商が仮想インベントリを開いている間、プレイヤーUUID → ロバ のマッピングを保持 */
    private final Map<UUID, ChestedHorse> openingDonkeys = new HashMap<>();

    /**
     * 現在誰かが開いているロバのEntityUUID。
     * 同一ロバを複数人が同時に開くことによるアイテム消失を防ぐ。
     */
    private final Set<UUID> lockedHorses = new HashSet<>();

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

        // 同じロバを別プレイヤーが既に開いている場合はブロック
        UUID horseId = horse.getUniqueId();
        if (lockedHorses.contains(horseId)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "このロバの荷車は別のプレイヤーが使用中です。");
            return;
        }

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

            lockedHorses.add(horseId);
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

        // ロックを解放
        lockedHorses.remove(horse.getUniqueId());

        // 閉じたインベントリの内容をロバの PDC に保存
        boolean saved = saveItems(player, horse, event.getInventory().getContents());
        if (!saved) {
            player.sendMessage(ChatColor.RED + "[エラー] 荷車のデータ保存に失敗しました！アイテムが消えた場合は運営に報告してください。");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // プレイヤー退出時のクリーンアップ
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // InventoryCloseEvent が先に発火するが念のためクリーンアップ
        ChestedHorse horse = openingDonkeys.remove(event.getPlayer().getUniqueId());
        if (horse != null) {
            lockedHorses.remove(horse.getUniqueId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ロバ死亡時: 拡張インベントリのアイテムをドロップ
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onDonkeyDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ChestedHorse horse)) return;
        if (!horse.isCarryingChest()) return;

        lockedHorses.remove(horse.getUniqueId());

        ItemStack[] items = loadItems(horse);
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                horse.getWorld().dropItemNaturally(horse.getLocation(), item);
            }
        }

        // PDCデータを削除
        horse.getPersistentDataContainer().remove(pdcKey);
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

    /**
     * @return 保存成功したら true、シリアライズ失敗なら false
     */
    private boolean saveItems(Player player, ChestedHorse horse, ItemStack[] items) {
        byte[] data = serializeItems(items);
        if (data.length == 0) {
            // シリアライズ失敗 — 保存しない（既存データを壊さない）
            return false;
        }
        PersistentDataContainer pdc = horse.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE_ARRAY, data);
        // PDC はサーバーの通常自動セーブで永続化される。world.save() は不要。
        return true;
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
