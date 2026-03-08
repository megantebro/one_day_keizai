package lobby.one_day_keizai.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.MerchantInventory;

public class WorldListener implements Listener {

    private final String pvpWorld;
    private final String economyWorld;

    public WorldListener(String pvpWorld, String economyWorld) {
        this.pvpWorld     = pvpWorld;
        this.economyWorld = economyWorld;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "ネザーとエンドへの移動は禁止されています。");
    }

    /**
     * PvPワールド（world）では敵対モブ以外の自然スポーンを禁止する。
     * スポーンエッグ・コマンド・プラグインによるスポーンは許可。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!pvpWorld.equals(event.getLocation().getWorld().getName())) return;

        // 自然スポーン以外は許可
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                && reason != CreatureSpawnEvent.SpawnReason.DEFAULT) return;

        // 敵対モブ（Monsterサブクラス）は許可
        if (event.getEntity() instanceof Monster) return;

        // それ以外（動物・中立・環境）はキャンセル
        event.setCancelled(true);
    }

    /**
     * 経済ワールド（economy）では敵対モブの自然スポーンを禁止する。
     * スポーンエッグ・コマンド・プラグインによるスポーンは許可。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawnEconomy(CreatureSpawnEvent event) {
        if (!economyWorld.equals(event.getLocation().getWorld().getName())) return;

        // 自然スポーン以外は許可
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                && reason != CreatureSpawnEvent.SpawnReason.DEFAULT) return;

        // 敵対モブ（Monsterサブクラス）のみキャンセル
        if (event.getEntity() instanceof Monster) {
            event.setCancelled(true);
        }
    }

    /**
     * 全ワールドでファントムの自然スポーンを禁止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Phantom)) return;
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            event.setCancelled(true);
        }
    }

    /**
     * 経済ワールド（economy）では感圧板の設置を禁止する。
     * OP は設置可能。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPressurePlaceEconomy(BlockPlaceEvent event) {
        if (!economyWorld.equals(event.getBlock().getWorld().getName())) return;
        if (!event.getBlock().getType().name().contains("PRESSURE_PLATE")) return;
        if (event.getPlayer().isOp()) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "経済ワールドでは感圧板を設置できません。");
    }

    /**
     * 経済ワールド（economy）では感圧板の破壊を OP 以外に禁止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPressureBreakEconomy(BlockBreakEvent event) {
        if (!economyWorld.equals(event.getBlock().getWorld().getName())) return;
        if (!event.getBlock().getType().name().contains("PRESSURE_PLATE")) return;
        if (event.getPlayer().isOp()) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "経済ワールドでは感圧板を破壊できません。");
    }

    /**
     * 馬（AbstractHorse）はスポーン時点で自動テイム済みにする。
     * どのプレイヤーでもそのまま乗れる。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHorseSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof AbstractHorse horse) {
            horse.setTamed(true);
        }
    }

    /**
     * PvPワールド（overworld）では村人・行商人との取引を禁止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!pvpWorld.equals(player.getWorld().getName())) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "オーバーワールドでは村人との取引はできません。");
    }

    /**
     * ゾンビ村人の治療による村人への変換をキャンセルする。
     * （治療による格安トレード取得悪用を防ぐ）
     * 経済ワールドは許可。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerCure(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) return;
        if (economyWorld.equals(event.getEntity().getWorld().getName())) return;
        event.setCancelled(true);
    }
}
