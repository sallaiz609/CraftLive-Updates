package hu.craftlive.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BedrockActionCatalogTest {
    @Test
    public void mobCommandsRoundTripThroughTheEditorCatalog() {
        String command = BedrockActionCatalog.command(
                BedrockActionCatalog.SPAWN_MOB, "warden", "");

        assertEquals("/summon warden ~ ~1 ~", command);
        assertEquals(BedrockActionCatalog.SPAWN_MOB, BedrockActionCatalog.detect(command));
        assertEquals("warden", BedrockActionCatalog.mobId(command));
    }

    @Test
    public void presetActionsBuildKnownBedrockCommands() {
        assertEquals("/summon lightning_bolt ~ ~ ~", BedrockActionCatalog.command(
                BedrockActionCatalog.LIGHTNING, "zombie", ""));
        assertEquals("/give @p diamond 3", BedrockActionCatalog.command(
                BedrockActionCatalog.GIVE_DIAMOND, "zombie", ""));
    }
}
