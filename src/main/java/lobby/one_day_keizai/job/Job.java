package lobby.one_day_keizai.job;

public enum Job {
    NONE("無職", "§7"),
    FARMER("農家", "§a"),
    BLACKSMITH("鍛冶屋", "§6"),
    MERCHANT("商人", "§b");

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
