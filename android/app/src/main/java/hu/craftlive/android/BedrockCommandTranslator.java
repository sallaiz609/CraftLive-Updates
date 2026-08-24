package hu.craftlive.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BedrockCommandTranslator {
    private BedrockCommandTranslator() {}

    public static List<String> translateMany(String raw, Map<String, String> variables) {
        ArrayList<String> commands = new ArrayList<>();
        if (raw == null) return commands;
        for (String part : raw.split(";;")) {
            String command = translate(part, variables);
            if (!command.trim().isEmpty()) commands.add(command);
        }
        return commands;
    }

    public static String translate(String raw, Map<String, String> variables) {
        if (raw == null) return "";
        String command = raw.trim();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            command = command.replace("{" + entry.getKey() + "}", sanitize(entry.getValue()));
        }
        if (!command.startsWith("/")) command = "/" + command;
        command = command.replace("minecraft:", "");

        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/effect give ")) {
            command = "/effect " + command.substring("/effect give ".length());
        } else if (lower.startsWith("/effect clear ")) {
            command = "/effect " + command.substring("/effect clear ".length()) + " clear";
        }
        return command.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^\\p{L}\\p{N}_ .@-]", "").trim();
    }
}
