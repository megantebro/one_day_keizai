package lobby.one_day_keizai.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class WorldListener implements Listener {

    /** PvPワールド名 */
    private static final String PVP_WORLD = "world";

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
}
