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

    public boolean isFarmer(UUID uuid) {
        return getJob(uuid) == Job.FARMER;
    }

    public boolean isBlacksmith(UUID uuid) {
        return getJob(uuid) == Job.BLACKSMITH;
    }

    public boolean isMerchant(UUID uuid) {
        return getJob(uuid) == Job.MERCHANT;
    }
}
