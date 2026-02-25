package lobby.one_day_keizai.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionManager {

    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final JavaPlugin plugin;
    private final NametagManager nametagManager;
    private final int protectionSeconds;

    public ProtectionManager(JavaPlugin plugin, NametagManager nametagManager,
                             int protectionSeconds) {
        this.plugin = plugin;
        this.nametagManager = nametagManager;
        this.protectionSeconds = protectionSeconds;
    }

    public boolean isProtected(UUID uuid) {
        Long until = protectedUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            protectedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public void applyProtection(Player player) {
        UUID uuid = player.getUniqueId();
        long until = System.currentTimeMillis() + (protectionSeconds * 1000L);
        protectedUntil.put(uuid, until);

        nametagManager.setProtected(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            protectedUntil.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // 罪人システム廃止につき isCriminal = false 固定
                nametagManager.updateNametag(p, false, false);
                p.sendMessage(ChatColor.YELLOW + "リスポーン保護が終了しました。");
            }
        }, protectionSeconds * 20L);
    }

    public void removeProtection(UUID uuid) {
        protectedUntil.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            nametagManager.updateNametag(p, false, false);
        }
    }
}
