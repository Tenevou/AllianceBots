package ru.alliancemc.alliancebots.bot;

public enum BotMode {
    CLIP,
    FIGHT;

    public static BotMode parse(String value) {
        if (value == null) {
            return CLIP;
        }
        if ("fight".equalsIgnoreCase(value)) {
            return FIGHT;
        }
        return CLIP;
    }
}
