package lobby.one_day_keizai.job;

import lobby.one_day_keizai.data.PlayerDataManager;

import java.util.UUID;

public class JobManager {

    private final PlayerDataManager dataManager;

    public JobManager(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * プレイヤーの職業を取得する。未設定の場合は NONE を返す。
     */
    public Job getJob(UUID uuid) {
        return dataManager.getJob(uuid);
    }

    /**
     * プレイヤーの職業を設定する。
     */
    public void setJob(UUID uuid, Job job) {
        dataManager.setJob(uuid, job);
        dataManager.save();
    }

    /** 農家 または 資本家（上級農家） */
    public boolean isFarmer(UUID uuid) {
        Job job = getJob(uuid);
        return job == Job.FARMER || job == Job.CAPITALIST;
    }

    /** 鍛冶屋 または エンチャンター（上級鍛冶屋） */
    public boolean isBlacksmith(UUID uuid) {
        Job job = getJob(uuid);
        return job == Job.BLACKSMITH || job == Job.ENCHANTER;
    }

    /** 商人 または 豪商（上級商人） */
    public boolean isMerchant(UUID uuid) {
        Job job = getJob(uuid);
        return job == Job.MERCHANT || job == Job.WEALTHY_MERCHANT;
    }

    /** 上級職: 資本家のみ */
    public boolean isCapitalist(UUID uuid) {
        return getJob(uuid) == Job.CAPITALIST;
    }

    /** 上級職: エンチャンターのみ */
    public boolean isEnchanter(UUID uuid) {
        return getJob(uuid) == Job.ENCHANTER;
    }

    /** 上級職: 豪商のみ */
    public boolean isWealthyMerchant(UUID uuid) {
        return getJob(uuid) == Job.WEALTHY_MERCHANT;
    }
}
