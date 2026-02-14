package lobby.one_day_keizai.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionManager {

    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final JavaPlugin plugin;
    private final NametagManager nametagManager;
    private final CriminalManager criminalManager;
    private final int protectionSeconds;

    public ProtectionManager(JavaPlugin plugin, NametagManager nametagManager,
                             CriminalManager criminalManager, int protectionSeconds) {
        this.plugin = plugin;
        this.nametagManager = nametagManager;
        this.criminalManager = criminalManager;
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

    /**
     * リスポーン保護を付与する。
     */
    public void applyProtection(Player player) {
        UUID uuid = player.getUniqueId();
        long until = System.currentTimeMillis() + (protectionSeconds * 1000L);
        protectedUntil.put(uuid, until);

        // 緑ネームタグ
        nametagManager.setProtected(player);

        // 保護終了時にネームタグを戻すタスク
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            protectedUntil.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                nametagManager.updateNametag(p,
                        criminalManager.isCriminal(uuid), false);
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "リスポーン保護が終了しました。");
            }
        }, protectionSeconds * 20L); // ticks
    }

    /**
     * 保護を手動解除する（攻撃しようとした場合など）。
     */
    public void removeProtection(UUID uuid) {
        protectedUntil.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            nametagManager.updateNametag(p,
                    criminalManager.isCriminal(uuid), false);
        }
    }
}
