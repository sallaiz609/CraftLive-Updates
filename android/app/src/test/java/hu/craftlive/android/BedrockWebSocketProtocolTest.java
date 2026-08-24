package hu.craftlive.android;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class BedrockWebSocketProtocolTest {
    @Test
    public void createsModernCommandRequestWithoutLeadingSlash() throws Exception {
        BedrockWebSocketProtocol.CommandRequest request =
                BedrockWebSocketProtocol.commandRequest("/summon zombie ~ ~1 ~");
        JSONObject root = new JSONObject(request.json);

        assertEquals("commandRequest",
                root.getJSONObject("header").getString("messagePurpose"));
        assertEquals(request.requestId,
                root.getJSONObject("header").getString("requestId"));
        assertEquals("summon zombie ~ ~1 ~",
                root.getJSONObject("body").getString("commandLine"));
        assertEquals("player",
                root.getJSONObject("body").getJSONObject("origin").getString("type"));
    }

    @Test
    public void parsesCommandResponseStatus() {
        BedrockWebSocketProtocol.CommandResponse response =
                BedrockWebSocketProtocol.parseResponse("{\"header\":{\"messagePurpose\":\"commandResponse\",\"requestId\":\"abc\"},\"body\":{\"statusCode\":0,\"statusMessage\":\"OK\"}}");

        assertNotNull(response);
        assertEquals("abc", response.requestId);
        assertEquals("OK", response.statusMessage);
        assertTrue(response.successful());
    }

    @Test
    public void rejectsEmptyCommand() {
        boolean thrown = false;
        try {
            BedrockWebSocketProtocol.commandRequest(" /// ");
        } catch (Throwable expected) {
            thrown = true;
        }
        assertTrue(thrown);
        assertFalse(BedrockConnectionAddresses.command("127.0.0.1").isEmpty());
    }
}
