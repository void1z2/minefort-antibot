package com.minefart.antibot;

import java.util.Locale;
import java.util.Set;

final class BanCommandBuilder {
    enum Mode {
        LITEBANS,
        ADVANCEDBAN,
        NORMAL
    }

    private BanCommandBuilder() {
    }

    static Mode detect(Set<String> plugins) {
        for (String plugin : plugins) {
            String name = plugin.toLowerCase(Locale.ROOT);
            if (name.equals("litebans")) return Mode.LITEBANS;
        }
        for (String plugin : plugins) {
            String name = plugin.toLowerCase(Locale.ROOT);
            if (name.equals("advancedban")) return Mode.ADVANCEDBAN;
        }
        return Mode.NORMAL;
    }

    static String build(Mode mode, String storedName, String reason, boolean silent) {
        String username = storedName.startsWith("+") || storedName.startsWith(".")
                ? storedName.substring(1) : storedName;
        String cleanReason = reason.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleanReason.isEmpty()) cleanReason = "Bot Account";

        if (silent && (mode == Mode.LITEBANS || mode == Mode.ADVANCEDBAN)) {
            return "ban -s " + username + " " + cleanReason;
        }
        return "ban " + username + " " + cleanReason;
    }
}

