package ru.alliancemc.alliancebots.bot;

public enum BotDifficulty {
    EASY,
    MEDIUM,
    HARD;

    public static BotDifficulty parse(String value) {
        if (value == null) {
            return MEDIUM;
        }
        if ("easy".equalsIgnoreCase(value)) {
            return EASY;
        }
        if ("hard".equalsIgnoreCase(value)) {
            return HARD;
        }
        return MEDIUM;
    }
}
