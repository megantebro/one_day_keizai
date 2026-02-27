package lobby.one_day_keizai.listener;

import lobby.one_day_keizai.job.JobManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class JobFarmListener implements Listener {

    private final JobManager jobManager;
    private final String safeWorldName;
    private final JavaPlugin plugin;

    /** 自動植え直しをOFFにしているプレイヤーUUID（デフォルトON） */
    private final Set<UUID> autoReplantDisabled = new HashSet<>();

    /** 農家専用の農作物ブロック一覧（設置・植え付けに使うアイテムのブロック形式） */
    private static final Set<Material> FARMER_ONLY_CROPS = EnumSet.of(
        Material.WHEAT,
        Material.WHEAT_SEEDS,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOT_SEEDS,
        Material.MELON_SEEDS,
        Material.PUMPKIN_SEEDS,
        Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER_SEEDS,
        Material.PITCHER_POD,
        Material.NETHER_WART,
        Material.COCOA,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS
    );

    /** 自動植え直し対象の作物（Ageableブロック） */
    private static final Set<Material> AUTO_REPLANT_CROPS = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART
    );

    /** 農家以外が収穫（破壊）できない作物ブロック一覧 */
    private static final Set<Material> HARVEST_ONLY_FARMER = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.SWEET_BERRY_BUSH,
        Material.NETHER_WART,
        Material.COCOA,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS,
        Material.MELON,
        Material.PUMPKIN
    );

    public JobFarmListener(JobManager jobManager, String safeWorldName, JavaPlugin plugin) {
        this.jobManager    = jobManager;
        this.safeWorldName = safeWorldName;
        this.plugin        = plugin;
    }

    // ─── 農作物の植え付け制限 ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material placed = event.getBlockPlaced().getType();
        if (placed == null || !FARMER_ONLY_CROPS.contains(placed)) return;

        if (!jobManager.isFarmer(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "農作物の栽培は農家のみ可能です。");
            player.sendMessage(ChatColor.YELLOW + "/job select と打って農家になれます。");
        }
    }

    // ─── 骨粉使用制限（農家のみ）─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoneMealUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) return;

        Player player = event.getPlayer();
        if (!jobManager.isFarmer(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "骨粉の使用は農家のみ可能です。");
        }
    }

    // ─── 農家以外の収穫禁止 ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCropHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!HARVEST_ONLY_FARMER.contains(block.getType())) return;

        Player player = event.getPlayer();
        if (player.isOp()) return;

        if (!jobManager.isFarmer(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "農作物の収穫は農家のみ可能です。");
        }
    }

    // ─── 自動植え直し（農家パッシブスキル）────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // 農家かつ自動植え直しONのみ
        if (!jobManager.isFarmer(player.getUniqueId())) return;
        if (autoReplantDisabled.contains(player.getUniqueId())) return;

        Block block = event.getBlock();
        if (!AUTO_REPLANT_CROPS.contains(block.getType())) return;

        // 最大成長済みかチェック
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        // 1tick後にブロックをage=0で植え直し
        final Material cropType = block.getType();
        final Location loc = block.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block target = loc.getBlock();
            // 既に何かあれば植えない（他プレイヤーが設置した等）
            if (target.getType() != Material.AIR) return;
            target.setType(cropType);
            if (target.getBlockData() instanceof Ageable a) {
                a.setAge(0);
                target.setBlockData(a);
            }
        }, 1L);
    }

    // ─── 自動植え直しトグル ───────────────────────────────────────

    public boolean toggleAutoReplant(UUID uuid) {
        if (autoReplantDisabled.contains(uuid)) {
            autoReplantDisabled.remove(uuid);
            return true;  // ON に戻した
        } else {
            autoReplantDisabled.add(uuid);
            return false; // OFF にした
        }
    }

    public boolean isAutoReplantEnabled(UUID uuid) {
        return !autoReplantDisabled.contains(uuid);
    }
}
