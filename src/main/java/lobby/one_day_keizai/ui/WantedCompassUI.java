package lobby.one_day_keizai.ui;

import lobby.one_day_keizai.manager.WantedManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 賞金首コンパス右クリック時に開くUI。
 * 現在オンラインの指名手配プレイヤーの頭を並べ、クリックで追跡TPする。
 */
public class WantedCompassUI implements Listener {

    private static final String TITLE = ChatColor.RED + "" + ChatColor.BOLD + "賞金首一覧";

    private final WantedManager wantedManager;
    private final JavaPlugin plugin;

    public WantedCompassUI(WantedManager wantedManager, JavaPlugin plugin) {
        this.wantedManager = wantedManager;
        this.plugin        = plugin;
    }

    // ─── UI 開く ─────────────────────────────────────────────────

    public void open(Player viewer) {
        // オンライン指名手配プレイヤーを収集
        List<Player> wantedOnline = new ArrayList<>();
        for (UUID uuid : wantedManager.getWantedUUIDs()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.equals(viewer)) {
                wantedOnline.add(p);
            }
        }

        if (wantedOnline.isEmpty()) {
            viewer.sendMessage(ChatColor.YELLOW + "現在オンラインの指名手配プレイヤーはいません。");
            return;
        }

        int rows = Math.max(1, (int) Math.ceil(wantedOnline.size() / 9.0));
        Inventory inv = Bukkit.createInventory(null, rows * 9, TITLE);

        for (Player wanted : wantedOnline) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta  = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(wanted);
            double bounty = wantedManager.getBounty(wanted.getUniqueId());
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + wanted.getName());
            meta.setLore(List.of(
                ChatColor.GOLD + "賞金: $" + String.format("%.0f", bounty),
                ChatColor.GRAY + "クリックで上空にTP",
                ChatColor.GRAY + "低速落下付き"
            ));
            skull.setItemMeta(meta);
            inv.addItem(skull);
        }

        viewer.openInventory(inv);
    }

    // ─── クリック処理 ─────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;

        String targetName = meta.getOwningPlayer().getName();
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(ChatColor.RED + targetName + " は現在オフラインです。");
            viewer.closeInventory();
            return;
        }
        if (!wantedManager.isWanted(target.getUniqueId())) {
            viewer.sendMessage(ChatColor.YELLOW + targetName + " の指名手配はすでに解除されています。");
            viewer.closeInventory();
            return;
        }

        viewer.closeInventory();
        teleportToTarget(viewer, target);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    // ─── TP + エフェクト ─────────────────────────────────────────

    private void teleportToTarget(Player viewer, Player target) {
        Location targetLoc = target.getLocation();
        World world = targetLoc.getWorld();

        // 対象の半径10〜50ブロック、上空15〜25ブロックにランダムTP
        double angle    = Math.random() * 2 * Math.PI;
        double distance = 10 + Math.random() * 40;          // 10〜50ブロック
        double heightUp = 15 + Math.random() * 10;          // 15〜25ブロック上

        Location tpLoc = targetLoc.clone().add(
            Math.cos(angle) * distance,
            heightUp,
            Math.sin(angle) * distance
        );
        // ワールド上限チェック
        tpLoc.setY(Math.min(tpLoc.getY(), world.getMaxHeight() - 5));
        // 向きを対象の方向に
        tpLoc.setYaw(getYawTowards(tpLoc, targetLoc));
        tpLoc.setPitch(20f);

        // ─ 出発地エフェクト ─
        Location from = viewer.getLocation();
        world.spawnParticle(Particle.PORTAL, from, 200, 0.5, 1.5, 0.5, 0.5);
        world.spawnParticle(Particle.FLAME,  from,  80, 0.4, 0.4, 0.4, 0.08);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.8f);
        world.playSound(from, Sound.BLOCK_END_PORTAL_SPAWN,   0.4f, 1.5f);

        // ─ TP 実行 ─
        viewer.teleport(tpLoc);

        // ─ 低速落下付与（5秒）─
        viewer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, true, true));

        // ─ お互いグロー（10秒）─
        viewer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, true, true));

        // ─ 到着エフェクト（1tick後） ─
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location dest = viewer.getLocation();
            world.spawnParticle(Particle.EXPLOSION_LARGE, dest, 3, 1, 0.5, 1, 0);
            world.spawnParticle(Particle.FIREWORKS_SPARK,  dest, 150, 1, 1, 1, 0.3);
            world.spawnParticle(Particle.DRAGON_BREATH, dest, 80, 0.5, 0.5, 0.5, 0.1);
            world.playSound(dest, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
            world.playSound(dest, Sound.ENTITY_FIREWORK_ROCKET_BLAST,  1.0f, 0.9f);
        }, 1L);

        viewer.sendMessage(ChatColor.RED + "" + ChatColor.BOLD
                + "▶ " + target.getName() + " の付近にTP！お互い10秒間発光します……");
    }

    private float getYawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }
}
