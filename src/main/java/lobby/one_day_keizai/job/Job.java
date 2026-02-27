package lobby.one_day_keizai.job;

public enum Job {
    NONE("無職", "§7"),
    FARMER("農家", "§a"),
    BLACKSMITH("鍛冶屋", "§6"),
    MERCHANT("商人", "§b"),
    CAPITALIST("資本家", "§2"),
    ENCHANTER("エンチャンター", "§e"),
    WEALTHY_MERCHANT("豪商", "§3");

    private final String displayName;
    private final String colorCode;

    Job(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    /** この職業が上級職かどうか */
    public boolean isUpperTier() {
        return this == CAPITALIST || this == ENCHANTER || this == WEALTHY_MERCHANT;
    }

    /**
     * 上級職ならその基本職を返す。基本職なら自分自身を返す。
     * 例: ENCHANTER → BLACKSMITH, FARMER → FARMER
     */
    public Job getBaseJob() {
        return switch (this) {
            case CAPITALIST      -> FARMER;
            case ENCHANTER       -> BLACKSMITH;
            case WEALTHY_MERCHANT -> MERCHANT;
            default              -> this;
        };
    }

    /**
     * 基本職からその上級職を返す。
     * 現在は鍛冶屋 → エンチャンターのみ昇格可能。
     */
    public Job getUpperJob() {
        return switch (this) {
            case BLACKSMITH -> ENCHANTER;
            case FARMER     -> CAPITALIST;
            case MERCHANT   -> WEALTHY_MERCHANT;
            default         -> null;
        };
    }

    /**
     * 表示名またはenum名から Job を解決する。大文字・小文字不問。
     */
    public static Job fromString(String s) {
        if (s == null) return NONE;
        for (Job job : values()) {
            if (job.name().equalsIgnoreCase(s) || job.displayName.equals(s)) {
                return job;
            }
        }
        return null;
    }
}
