package lobby.one_day_keizai.ui;

import lobby.one_day_keizai.job.Job;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * 職業選択インベントリGUI。
 * 無職プレイヤーがログイン時に表示される。
 *
 * レイアウト (27スロット / 3行):
 * ┌─────────────────────────────┐
 * │  G  G  G  G  G  G  G  G  G │  ← 装飾ガラス
 * │  G  農  G  鍛  G  商  G  後 G│  ← 選択ボタン
 * │  G  G  G  G  G  G  G  G  G │  ← 装飾ガラス
 * └─────────────────────────────┘
 *
 * スロット: 農家=10, 鍛冶屋=12, 商人=14, 後で=16
 */
public class JobSelectionUI {

    public static final String TITLE = ChatColor.GOLD + "職業を選択してください";

    // 職業ボタンのスロット番号
    public static final int SLOT_FARMER     = 10;
    public static final int SLOT_BLACKSMITH = 12;
    public static final int SLOT_MERCHANT   = 14;
    public static final int SLOT_LATER      = 16;

    private static final int SIZE = 27;

    private JobSelectionUI() {}

    /**
     * 職業選択UIを開く。
     */
    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // 全スロットをガラスで埋める
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }

        // 職業ボタン配置
        inv.setItem(SLOT_FARMER,     makeFarmerItem());
        inv.setItem(SLOT_BLACKSMITH, makeBlacksmithItem());
        inv.setItem(SLOT_MERCHANT,   makeMerchantItem());
        inv.setItem(SLOT_LATER,      makeLaterItem());

        player.openInventory(inv);
    }

    /** このインベントリが職業選択UIかどうか判定する。 */
    public static boolean isJobSelectionUI(String title) {
        return TITLE.equals(title);
    }

    /** スロット番号から選択された Job を返す。選択なし（後で/関係ないスロット）は null。 */
    public static Job getJobForSlot(int slot) {
        return switch (slot) {
            case SLOT_FARMER     -> Job.FARMER;
            case SLOT_BLACKSMITH -> Job.BLACKSMITH;
            case SLOT_MERCHANT   -> Job.MERCHANT;
            default              -> null;
        };
    }

    // =========================================
    // アイテム生成
    // =========================================

    private static ItemStack makeFarmerItem() {
        ItemStack item = makeItem(Material.WHEAT, Job.FARMER.getColorCode() + "農家");
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Arrays.asList(
                ChatColor.WHITE + "安全ワールドで農作物を栽培できます",
                ChatColor.WHITE + "農作物をショップで売却できます",
                "",
                ChatColor.YELLOW + "クリックして選択"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeBlacksmithItem() {
        ItemStack item = makeItem(Material.IRON_PICKAXE, Job.BLACKSMITH.getColorCode() + "鍛冶屋");
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Arrays.asList(
                ChatColor.WHITE + "鉄・金・ダイヤ・ネザライトの",
                ChatColor.WHITE + "ツール/装備をクラフトできます",
                "",
                ChatColor.YELLOW + "クリックして選択"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeMerchantItem() {
        ItemStack item = makeItem(Material.EMERALD, Job.MERCHANT.getColorCode() + "商人");
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Arrays.asList(
                ChatColor.WHITE + "危険ワールドの遠方ショップに",
                ChatColor.WHITE + "アクセスできます",
                "",
                ChatColor.YELLOW + "クリックして選択"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeLaterItem() {
        ItemStack item = makeItem(Material.BARRIER, ChatColor.GRAY + "後で選ぶ");
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "次回ログイン時にまた表示されます",
                ChatColor.GRAY + "/job select で後からでも選べます"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
