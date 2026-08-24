package hu.craftlive.android;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class BedrockCommandTranslatorTest {
    @Test
    public void translatesJavaEffectSyntaxToBedrock() {
        assertEquals("/effect @p regeneration 10 1 true",
                BedrockCommandTranslator.translate(
                        "/effect give @p minecraft:regeneration 10 1 true", Map.of()));
    }

    @Test
    public void removesUnsafePlaceholderCharacters() {
        assertEquals("/say user_name",
                BedrockCommandTranslator.translate("/say {user}",
                        Map.of("user", "user_name;/kill @a")));
    }

    @Test
    public void splitsMultiCommandPreset() {
        assertEquals(2, BedrockCommandTranslator.translateMany(
                "/time set night;;/weather thunder", Map.of()).size());
    }
}
