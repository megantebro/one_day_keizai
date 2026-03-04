package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.manager.AirdropManager;
import lobby.one_day_keizai.manager.WorldManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * エアドロップイベントハンドラ。
 * - 右クリック + 鍵所持 → 開錠タイマー開始
 * - 通常のインベントリ開けをブロック（鍵なしでは中身見られない）
 * - ワールド移動 → 脱出判定 / 開錠キャンセル
 * - 死亡 → 開錠キャンセル＆発光解除
 */
public class AirdropListener implements Listener {

    private final AirdropManager airdropManager;
    private final WorldManager   worldManager;

    public AirdropListener(AirdropManager airdropManager, WorldManager worldManager) {
        this.airdropManager = airdropManager;
        this.worldManager   = worldManager;
    }

    /**
     * プレイヤーがブロックを右クリック。
     * アイドロップクレート（チェスト）の場合は通常の開封をキャンセルし、
     * 鍵があれば開錠タイマーを開始する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // メインハンドのみ処理（オフハンドで二重発火しない）
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        // クレートかどうか確認（違えば通常の動作を許可）
        if (!airdropManager.isAirdropChest(block.getLocation())) return;

        // クレートなので通常のインベントリ開封は常にキャンセル
        event.setCancelled(true);

        Player player = event.getPlayer();
        airdropManager.tryStartOpening(player, block.getLocation(), event.getItem());
    }

    /**
     * InventoryOpenEvent でもクレートを直接開けないようブロック（念のため二重防止）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest)) return;

        if (airdropManager.isAirdropChest(chest.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * ワールド移動。
     * - 安全ワールドへ移動 → 発光解除（脱出成功）
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // 安全ワールドへ移動した場合は発光解除
        if (worldManager.isSafeWorld(player.getWorld())) {
            airdropManager.onPlayerEscape(player);
        }
    }

    /** 死亡時：開錠キャンセル＆発光解除 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        airdropManager.onPlayerDeath(event.getEntity());
    }

    /** クレートチェストの破壊を禁止 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        if (!airdropManager.isAirdropChest(block.getLocation())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(
                org.bukkit.ChatColor.RED + "エアドロップクレートは破壊できません。");
    }

    /** 爆発によるクレートチェストの破壊を禁止 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(
                b -> b.getType() == Material.CHEST && airdropManager.isAirdropChest(b.getLocation()));
    }

    /** ブロック爆発によるクレートチェストの破壊を禁止 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(
                b -> b.getType() == Material.CHEST && airdropManager.isAirdropChest(b.getLocation()));
    }
}
