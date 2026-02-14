package lobby.one_day_keizai.manager;

import lobby.one_day_keizai.data.PlayerDataManager;

import java.util.UUID;

public class CriminalManager {

    private final PlayerDataManager dataManager;
    private final int innocentKillLimit;

    public CriminalManager(PlayerDataManager dataManager, int innocentKillLimit) {
        this.dataManager = dataManager;
        this.innocentKillLimit = innocentKillLimit;
    }

    public boolean isCriminal(UUID uuid) {
        return dataManager.isCriminal(uuid);
    }

    public void setCriminal(UUID uuid, boolean criminal) {
        dataManager.setCriminal(uuid, criminal);
    }

    public int getInnocentKillCount(UUID uuid) {
        return dataManager.getInnocentKillCount(uuid);
    }

    /**
     * 無実キルカウントを1増やし、上限に達したら罪人化する。
     * @return 罪人化した場合true
     */
    public boolean incrementInnocentKill(UUID uuid) {
        int count = dataManager.getInnocentKillCount(uuid) + 1;
        dataManager.setInnocentKillCount(uuid, count);
        if (count >= innocentKillLimit) {
            dataManager.setCriminal(uuid, true);
            return true;
        }
        return false;
    }
}
