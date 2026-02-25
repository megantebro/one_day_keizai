package lobby.one_day_keizai.data;

import lobby.one_day_keizai.stock.StockOffer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 株の保有数と P2P 売却オファーを stockdata.yml で永続化する。
 *
 * <pre>
 * holdings:
 *   <uuid>: <shares int>
 *
 * offers:
 *   <offer-uuid>:
 *     from: <uuid>
 *     to:   <uuid>
 *     amount: <int>
 *     price:  <double>
 *     timestamp: <long>
 * </pre>
 */
public class StockDataManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private YamlConfiguration cfg;

    public StockDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "stockdata.yml");
        load();
    }

    // ─── I/O ────────────────────────────────────────────────────────────────

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "stockdata.yml の作成に失敗しました", e);
            }
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "stockdata.yml の保存に失敗しました", e);
        }
    }

    // ─── Holdings ───────────────────────────────────────────────────────────

    public int getHoldings(UUID uuid) {
        return cfg.getInt("holdings." + uuid, 0);
    }

    public void setHoldings(UUID uuid, int shares) {
        if (shares <= 0) {
            cfg.set("holdings." + uuid, null);
        } else {
            cfg.set("holdings." + uuid, shares);
        }
    }

    public void addHoldings(UUID uuid, int delta) {
        setHoldings(uuid, getHoldings(uuid) + delta);
    }

    /**
     * 株を保有している全プレイヤーの UUID → 保有数 マップを返す。
     */
    public Map<UUID, Integer> getAllHoldings() {
        Map<UUID, Integer> map = new HashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("holdings");
        if (sec == null) return map;
        for (String key : sec.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), sec.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return map;
    }

    public int getTotalShares() {
        return getAllHoldings().values().stream().mapToInt(Integer::intValue).sum();
    }

    // ─── Offers ─────────────────────────────────────────────────────────────

    public void addOffer(StockOffer offer) {
        String base = "offers." + offer.id;
        cfg.set(base + ".from",      offer.fromUuid.toString());
        cfg.set(base + ".to",        offer.toUuid.toString());
        cfg.set(base + ".amount",    offer.amount);
        cfg.set(base + ".price",     offer.price);
        cfg.set(base + ".timestamp", offer.timestamp);
    }

    public void removeOffer(UUID offerId) {
        cfg.set("offers." + offerId, null);
    }

    public List<StockOffer> getAllOffers() {
        List<StockOffer> list = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("offers");
        if (sec == null) return list;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(key);
            if (entry == null) continue;
            try {
                list.add(new StockOffer(
                        UUID.fromString(key),
                        UUID.fromString(entry.getString("from")),
                        UUID.fromString(entry.getString("to")),
                        entry.getInt("amount"),
                        entry.getDouble("price"),
                        entry.getLong("timestamp")
                ));
            } catch (Exception ignored) {}
        }
        return list;
    }

    /** 特定プレイヤーに向けられたオファー一覧 */
    public List<StockOffer> getOffersTo(UUID toUuid) {
        List<StockOffer> result = new ArrayList<>();
        for (StockOffer o : getAllOffers()) {
            if (o.toUuid.equals(toUuid)) result.add(o);
        }
        return result;
    }

    /** 特定プレイヤーが送ったオファー一覧 */
    public List<StockOffer> getOffersFrom(UUID fromUuid) {
        List<StockOffer> result = new ArrayList<>();
        for (StockOffer o : getAllOffers()) {
            if (o.fromUuid.equals(fromUuid)) result.add(o);
        }
        return result;
    }

    /** from → to へのオファーを1件取得（複数ある場合は最新） */
    public Optional<StockOffer> getOffer(UUID fromUuid, UUID toUuid) {
        return getAllOffers().stream()
                .filter(o -> o.fromUuid.equals(fromUuid) && o.toUuid.equals(toUuid))
                .max(Comparator.comparingLong(o -> o.timestamp));
    }
}
