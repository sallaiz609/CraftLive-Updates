package hu.craftlive.android;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

final class BedrockWebSocketProtocol {
    private BedrockWebSocketProtocol() {
    }

    static CommandRequest commandRequest(String rawCommand) throws JSONException {
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isEmpty()) throw new IllegalArgumentException("Empty Bedrock command");

        String requestId = UUID.randomUUID().toString();
        JSONObject header = new JSONObject()
                .put("version", 1)
                .put("requestId", requestId)
                .put("messageType", "commandRequest")
                .put("messagePurpose", "commandRequest");
        JSONObject origin = new JSONObject().put("type", "player");
        JSONObject body = new JSONObject()
                .put("version", 1)
                .put("commandLine", command)
                .put("origin", origin);
        return new CommandRequest(requestId, new JSONObject()
                .put("header", header)
                .put("body", body)
                .toString());
    }

    static CommandResponse parseResponse(String rawMessage) {
        try {
            JSONObject root = new JSONObject(rawMessage);
            JSONObject header = root.optJSONObject("header");
            if (header == null) return null;
            String purpose = header.optString("messagePurpose",
                    header.optString("messageType", ""));
            if (!"commandResponse".equals(purpose)) return null;
            JSONObject body = root.optJSONObject("body");
            int statusCode = body == null ? -1 : body.optInt("statusCode", -1);
            String statusMessage = body == null ? "" : body.optString("statusMessage", "");
            return new CommandResponse(
                    header.optString("requestId", ""), statusCode, statusMessage);
        } catch (JSONException ignored) {
            return null;
        }
    }

    static final class CommandRequest {
        final String requestId;
        final String json;

        CommandRequest(String requestId, String json) {
            this.requestId = requestId;
            this.json = json;
        }
    }

    static final class CommandResponse {
        final String requestId;
        final int statusCode;
        final String statusMessage;

        CommandResponse(String requestId, int statusCode, String statusMessage) {
            this.requestId = requestId;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
        }

        boolean successful() {
            return statusCode >= 0 && statusCode < 100;
        }
    }
}
