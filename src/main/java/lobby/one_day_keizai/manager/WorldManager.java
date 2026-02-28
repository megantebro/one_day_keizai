package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class WorldManager {

    private final JavaPlugin plugin;
    private final Economy economy;
    private final PlayerDataManager playerDataManager;
    private final String safeWorldName;
    private final String overworldName;
    private final Set<UUID> diedInOverworld = new HashSet<>();

    /** 入場料上限 */
    private static final double MAX_ENTRY_FEE = 20000.0;
    /** 入場料率（所持金の10%） */
    private static final double ENTRY_FEE_RATE = 0.1;

    public WorldManager(JavaPlugin plugin, Economy economy, PlayerDataManager playerDataManager,
                        String safeWorldName, String overworldName) {
        this.plugin = plugin;
        this.economy = economy;
        this.playerDataManager = playerDataManager;
        this.safeWorldName = safeWorldName;
        this.overworldName = overworldName;
    }

    /**
     * 入場料を動的計算（所持金の10%、上限2万）
     */
    public double calculateEntryFee(double balance) {
        return Math.min(balance * ENTRY_FEE_RATE, MAX_ENTRY_FEE);
    }

    /**
     * オーバーワールドへ入場する。入場料を徴収しデポジットとして記録。
     */
    public boolean enterOverworld(Player player) {
        if (isInOverworld(player)) {
            player.sendMessage(ChatColor.RED + "すでにオーバーワールドにいます。");
            return false;
        }

        World overworld = Bukkit.getWorld(overworldName);
        if (overworld == null) {
            player.sendMessage(ChatColor.RED + "オーバーワールドが見つかりません。");
            return false;
        }

        double balance = economy.getBalance(player);
        double entryFee = calculateEntryFee(balance);

        economy.withdrawPlayer(player, entryFee);
        playerDataManager.setOverworldDeposit(player.getUniqueId(), entryFee);

        // スポーン付近50ブロック以内のランダム位置・上空30〜50ブロックにTP
        Location spawn = overworld.getSpawnLocation();
        Random rng = new Random();
        double angle    = rng.nextDouble() * 2 * Math.PI;
        double distance = 10 + rng.nextDouble() * 40;   // 10〜50ブロック
        double tpX = spawn.getX() + Math.cos(angle) * distance;
        double tpZ = spawn.getZ() + Math.sin(angle) * distance;
        double tpY = Math.min(spawn.getY() + 30 + rng.nextDouble() * 20, overworld.getMaxHeight() - 5);
        Location tpLoc = new Location(overworld, tpX, tpY, tpZ, spawn.getYaw(), 30f);

        // 出発エフェクト
        try {
            Location from = player.getLocation();
            from.getWorld().spawnParticle(Particle.PORTAL, from, 150, 0.4, 1.0, 0.4, 0.4);
            from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldManager] 出発エフェクト中にエラー: " + e.getMessage());
        }

        player.teleport(tpLoc);

        // 低速落下（8秒）・耐性III（20秒）
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 400, 2, false, true, true));

        player.sendMessage(ChatColor.GREEN + "オーバーワールドに入場しました！上空から降下中……");
        if (entryFee > 0) {
            player.sendMessage(ChatColor.YELLOW + "入場料: " + ChatColor.GOLD + String.format("$%.0f", entryFee)
                    + ChatColor.YELLOW + "（所持金の10%、上限$20,000）");
            player.sendMessage(ChatColor.YELLOW + "生還すれば " +
                    ChatColor.GOLD + String.format("$%.0f", entryFee) +
                    ChatColor.YELLOW + " が全額返金されます。");
        }
        return true;
    }

    /**
     * 安全ワールドへ帰還する。デポジットを全額返金。
     */
    public boolean returnToSafeWorld(Player player) {
        if (!isInOverworld(player)) {
            player.sendMessage(ChatColor.RED + "オーバーワールドにいません。");
            return false;
        }

        World safeWorld = Bukkit.getWorld(safeWorldName);
        if (safeWorld == null) {
            player.sendMessage(ChatColor.RED + "安全ワールドが見つかりません。");
            return false;
        }

        UUID playerId = player.getUniqueId();
        double deposit = playerDataManager.getOverworldDeposit(playerId);

        if (deposit > 0) {
            economy.depositPlayer(player, deposit);
        }
        playerDataManager.clearOverworldDeposit(playerId);

        player.teleport(safeWorld.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "安全ワールドに帰還しました。");
        if (deposit > 0) {
            player.sendMessage(ChatColor.GREEN + "返金額: " + ChatColor.GOLD + String.format("$%.0f", deposit)
                    + ChatColor.GREEN + "（全額返金）");
        }
        return true;
    }

    /**
     * オーバーワールドで死亡した場合の処理。デポジット没収 + リスポーン先を安全ワールドにマーク。
     */
    public void handleOverworldDeath(Player player) {
        UUID playerId = player.getUniqueId();
        double deposit = playerDataManager.getOverworldDeposit(playerId);
        playerDataManager.clearOverworldDeposit(playerId);
        diedInOverworld.add(playerId);

        if (deposit > 0) {
            player.sendMessage(ChatColor.RED + "オーバーワールドで死亡したため、入場料 " +
                    ChatColor.GOLD + String.format("$%.0f", deposit) +
                    ChatColor.RED + " が没収されました。");
        }
    }

    /**
     * オーバーワールドで死亡したかを確認し、フラグをクリアする。
     */
    public boolean consumeDiedInOverworld(UUID playerId) {
        return diedInOverworld.remove(playerId);
    }

    public boolean isInOverworld(Player player) {
        return player.getWorld().getName().equals(overworldName);
    }

    public boolean isInSafeWorld(Player player) {
        return player.getWorld().getName().equals(safeWorldName);
    }

    public boolean isOverworld(World world) {
        return world.getName().equals(overworldName);
    }

    public boolean isSafeWorld(World world) {
        return world.getName().equals(safeWorldName);
    }

    public String getSafeWorldName() {
        return safeWorldName;
    }

    public String getOverworldName() {
        return overworldName;
    }

    /** 後方互換用: プレイヤーの現在残高から入場料を返す */
    public double getEntryFee(Player player) {
        return calculateEntryFee(economy.getBalance(player));
    }
}
