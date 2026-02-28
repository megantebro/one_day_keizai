package lobby.one_day_keizai.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class WorldListener implements Listener {

    /** PvPワールド名 */
    private static final String PVP_WORLD = "world";
    /** 経済ワールド名 */
    private static final String ECONOMY_WORLD = "economy";

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
        if (!PVP_WORLD.equals(event.getLocation().getWorld().getName())) return;

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
        if (!ECONOMY_WORLD.equals(event.getLocation().getWorld().getName())) return;

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
     * 経済ワールド（economy）では感圧板の設置を禁止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPressurePlaceEconomy(BlockPlaceEvent event) {
        if (!ECONOMY_WORLD.equals(event.getBlock().getWorld().getName())) return;
        if (!event.getBlock().getType().name().contains("PRESSURE_PLATE")) return;

        event.setCancelled(true);
        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(ChatColor.RED + "経済ワールドでは感圧板を設置できません。");
        }
    }
}
