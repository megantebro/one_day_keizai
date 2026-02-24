package lobby.one_day_keizai.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "playerdata.yml の作成に失敗しました", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "playerdata.yml の保存に失敗しました", e);
        }
    }

    // --- Innocent Kill Count ---

    public int getInnocentKillCount(UUID uuid) {
        return dataConfig.getInt("players." + uuid + ".innocentKills", 0);
    }

    public void setInnocentKillCount(UUID uuid, int count) {
        dataConfig.set("players." + uuid + ".innocentKills", count);
    }

    // --- Criminal Status ---

    public boolean isCriminal(UUID uuid) {
        return dataConfig.getBoolean("players." + uuid + ".isCriminal", false);
    }

    public void setCriminal(UUID uuid, boolean criminal) {
        dataConfig.set("players." + uuid + ".isCriminal", criminal);
    }

    // --- Bed Location ---

    public Location getBedLocation(UUID uuid) {
        String path = "players." + uuid + ".bedLocation";
        if (!dataConfig.contains(path + ".world")) {
            return null;
        }
        String worldName = dataConfig.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        return new Location(world, x, y, z);
    }

    public void setBedLocation(UUID uuid, Location loc) {
        String path = "players." + uuid + ".bedLocation";
        if (loc == null) {
            dataConfig.set(path, null);
            return;
        }
        dataConfig.set(path + ".world", loc.getWorld().getName());
        dataConfig.set(path + ".x", loc.getX());
        dataConfig.set(path + ".y", loc.getY());
        dataConfig.set(path + ".z", loc.getZ());
    }

    // --- Logout Penalty Data ---

    public void setLogoutPenaltyData(UUID uuid, Location loc, ItemStack[] inventory, long deadline) {
        String path = "logoutPenalty." + uuid;
        dataConfig.set(path + ".world", loc.getWorld().getName());
        dataConfig.set(path + ".x", loc.getX());
        dataConfig.set(path + ".y", loc.getY());
        dataConfig.set(path + ".z", loc.getZ());
        dataConfig.set(path + ".deadline", deadline);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item != null) {
                items.add(item.serialize());
            }
        }
        dataConfig.set(path + ".inventory", items);
    }

    public boolean hasLogoutPenaltyData(UUID uuid) {
        return dataConfig.contains("logoutPenalty." + uuid);
    }

    public long getLogoutPenaltyDeadline(UUID uuid) {
        return dataConfig.getLong("logoutPenalty." + uuid + ".deadline", 0);
    }

    @SuppressWarnings("unchecked")
    public ItemStack[] getLogoutPenaltyInventory(UUID uuid) {
        String path = "logoutPenalty." + uuid + ".inventory";
        List<?> items = dataConfig.getList(path);
        if (items == null) return new ItemStack[0];
        List<ItemStack> result = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof Map) {
                result.add(ItemStack.deserialize((Map<String, Object>) obj));
            }
        }
        return result.toArray(new ItemStack[0]);
    }

    public void clearLogoutPenaltyData(UUID uuid) {
        dataConfig.set("logoutPenalty." + uuid, null);
    }

    // --- Overworld Deposit ---

    public void setOverworldDeposit(UUID uuid, double amount) {
        dataConfig.set("overworldDeposit." + uuid, amount);
    }

    public double getOverworldDeposit(UUID uuid) {
        return dataConfig.getDouble("overworldDeposit." + uuid, 0.0);
    }

    public boolean hasOverworldDeposit(UUID uuid) {
        return dataConfig.contains("overworldDeposit." + uuid);
    }

    public void clearOverworldDeposit(UUID uuid) {
        dataConfig.set("overworldDeposit." + uuid, null);
    }

    // --- Debt Data ---

    public void saveDebts(List<Map<String, Object>> debts) {
        dataConfig.set("debts", debts);
    }

    public List<Map<String, Object>> loadDebts() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<?> list = dataConfig.getList("debts");
        if (list == null) return result;
        for (Object obj : list) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                result.add(map);
            }
        }
        return result;
    }
}
