package lobby.one_day_keizai.stock;

import java.util.UUID;

/**
 * P2P 株売却オファー。
 * fromUuid → toUuid へ amount 株を price/株 で売るオファー。
 */
public class StockOffer {

    public final UUID id;
    public final UUID fromUuid;
    public final UUID toUuid;
    public final int amount;
    public final double price; // per share
    public final long timestamp;

    public StockOffer(UUID fromUuid, UUID toUuid, int amount, double price) {
        this.id = UUID.randomUUID();
        this.fromUuid = fromUuid;
        this.toUuid = toUuid;
        this.amount = amount;
        this.price = price;
        this.timestamp = System.currentTimeMillis();
    }

    /** YAML 復元用コンストラクタ */
    public StockOffer(UUID id, UUID fromUuid, UUID toUuid, int amount, double price, long timestamp) {
        this.id = id;
        this.fromUuid = fromUuid;
        this.toUuid = toUuid;
        this.amount = amount;
        this.price = price;
        this.timestamp = timestamp;
    }
}
