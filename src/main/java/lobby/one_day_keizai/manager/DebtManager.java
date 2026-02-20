package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DebtManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager dataManager;
    private final CriminalManager criminalManager;
    private final NametagManager nametagManager;
    private final List<Debt> debts = new ArrayList<>();

    public DebtManager(JavaPlugin plugin, PlayerDataManager dataManager,
                       CriminalManager criminalManager, NametagManager nametagManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.criminalManager = criminalManager;
        this.nametagManager = nametagManager;
        loadDebts();
    }

    public static class Debt {
        public UUID creditor;
        public UUID debtor;
        public double amount;
        public long deadline; // timestamp
        public double interestRate; // 利率（%）

        public Debt(UUID creditor, UUID debtor, double amount, long deadline, double interestRate) {
            this.creditor = creditor;
            this.debtor = debtor;
            this.amount = amount;
            this.deadline = deadline;
            this.interestRate = interestRate;
        }
    }

    public void addDebt(UUID creditor, UUID debtor, double amount, long deadlineTimestamp, double interestRate) {
        double totalAmount = amount * (1 + interestRate / 100.0);
        debts.add(new Debt(creditor, debtor, totalAmount, deadlineTimestamp, interestRate));
        saveDebts();
    }

    public boolean isDebtor(UUID uuid) {
        for (Debt debt : debts) {
            if (debt.debtor.equals(uuid)) return true;
        }
        return false;
    }

    public UUID getCreditor(UUID debtor) {
        for (Debt debt : debts) {
            if (debt.debtor.equals(debtor)) return debt.creditor;
        }
        return null;
    }

    public List<Debt> getDebtsForPlayer(UUID uuid) {
        List<Debt> result = new ArrayList<>();
        for (Debt debt : debts) {
            if (debt.creditor.equals(uuid) || debt.debtor.equals(uuid)) {
                result.add(debt);
            }
        }
        return result;
    }

    /**
     * 特定の債権者への返済。
     * @return 返済額
     */
    public double repay(UUID debtor, UUID creditor, double amount) {
        Iterator<Debt> it = debts.iterator();
        double totalRepaid = 0;
        while (it.hasNext()) {
            Debt debt = it.next();
            if (debt.debtor.equals(debtor) && debt.creditor.equals(creditor)) {
                if (amount >= debt.amount) {
                    totalRepaid += debt.amount;
                    amount -= debt.amount;
                    it.remove();
                } else {
                    debt.amount -= amount;
                    totalRepaid += amount;
                    amount = 0;
                }
                if (amount <= 0) break;
            }
        }
        saveDebts();
        return totalRepaid;
    }

    /**
     * 債権者が債務者の債務を許す。
     */
    public boolean forgive(UUID creditor, UUID debtor) {
        boolean found = false;
        Iterator<Debt> it = debts.iterator();
        while (it.hasNext()) {
            Debt debt = it.next();
            if (debt.creditor.equals(creditor) && debt.debtor.equals(debtor)) {
                it.remove();
                found = true;
            }
        }
        if (found) saveDebts();
        return found;
    }

    /**
     * 債務者の全債務を帳消しにする（死亡時）。
     */
    public void clearDebtsForDebtor(UUID debtor) {
        debts.removeIf(debt -> debt.debtor.equals(debtor));
        saveDebts();
    }

    /**
     * 期限切れ債務をチェックし、債務者を罪人化する。
     */
    public void checkDeadlines() {
        long now = System.currentTimeMillis();
        Iterator<Debt> it = debts.iterator();
        while (it.hasNext()) {
            Debt debt = it.next();
            if (now > debt.deadline) {
                // 期限切れ → 債務者を罪人化
                criminalManager.setCriminal(debt.debtor, true);
                Player debtor = Bukkit.getPlayer(debt.debtor);
                if (debtor != null) {
                    debtor.sendMessage(ChatColor.DARK_RED + "債務の期限が切れました。罪人になりました！");
                    nametagManager.setCriminal(debtor);
                }
                Player creditor = Bukkit.getPlayer(debt.creditor);
                if (creditor != null) {
                    creditor.sendMessage(ChatColor.RED + "債務者の返済期限が切れました。債務者は罪人になりました。");
                }
                it.remove();
            }
        }
        saveDebts();
    }

    /**
     * 定期チェックタスクを開始する。
     */
    public void startDeadlineChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkDeadlines, 20L * 60, 20L * 60); // 毎分チェック
    }

    private void saveDebts() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Debt debt : debts) {
            Map<String, Object> map = new HashMap<>();
            map.put("creditor", debt.creditor.toString());
            map.put("debtor", debt.debtor.toString());
            map.put("amount", debt.amount);
            map.put("deadline", debt.deadline);
            map.put("interestRate", debt.interestRate);
            list.add(map);
        }
        dataManager.saveDebts(list);
        dataManager.save();
    }

    private void loadDebts() {
        List<Map<String, Object>> list = dataManager.loadDebts();
        for (Map<String, Object> map : list) {
            UUID creditor = UUID.fromString((String) map.get("creditor"));
            UUID debtor = UUID.fromString((String) map.get("debtor"));
            double amount = ((Number) map.get("amount")).doubleValue();
            long deadline = ((Number) map.get("deadline")).longValue();
            double interestRate = map.containsKey("interestRate") ? ((Number) map.get("interestRate")).doubleValue() : 0;
            debts.add(new Debt(creditor, debtor, amount, deadline, interestRate));
        }
    }
}
