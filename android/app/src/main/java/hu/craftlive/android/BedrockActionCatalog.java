package hu.craftlive.android;

import java.util.Locale;

final class BedrockActionCatalog {
    static final int SPAWN_MOB = 0;
    static final int LIGHTNING = 1;
    static final int GIVE_DIAMOND = 2;
    static final int HEAL = 3;
    static final int DAY = 4;
    static final int THUNDER = 5;
    static final int CUSTOM = 6;

    private BedrockActionCatalog() {
    }

    static String[] labels(boolean hungarian) {
        return hungarian
                ? new String[]{"Mob idézése", "Villámcsapás", "Gyémánt adása", "Gyógyítás",
                "Nappal", "Vihar", "Egyéni parancs (haladó)"}
                : new String[]{"Spawn mob", "Lightning strike", "Give diamonds", "Heal player",
                "Daytime", "Thunderstorm", "Custom command (advanced)"};
    }

    static int detect(String command) {
        String value = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/summon ") && !value.startsWith("/summon lightning_bolt")) return SPAWN_MOB;
        if (value.startsWith("/summon lightning_bolt")) return LIGHTNING;
        if (value.startsWith("/give @p diamond")) return GIVE_DIAMOND;
        if (value.startsWith("/effect @p regeneration")) return HEAL;
        if (value.startsWith("/time set day")) return DAY;
        if (value.startsWith("/weather thunder")) return THUNDER;
        return CUSTOM;
    }

    static String mobId(String command) {
        String value = command == null ? "" : command.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith("/summon ")) return "zombie";
        String[] parts = value.split("\\s+");
        return parts.length >= 2 ? parts[1].replace("minecraft:", "") : "zombie";
    }

    static String command(int action, String mobId, String custom) {
        return switch (action) {
            case SPAWN_MOB -> "/summon " + mobId + " ~ ~1 ~";
            case LIGHTNING -> "/summon lightning_bolt ~ ~ ~";
            case GIVE_DIAMOND -> "/give @p diamond 3";
            case HEAL -> "/effect @p regeneration 10 1 true";
            case DAY -> "/time set day";
            case THUNDER -> "/weather thunder 120";
            default -> custom == null ? "" : custom.trim();
        };
    }
}
