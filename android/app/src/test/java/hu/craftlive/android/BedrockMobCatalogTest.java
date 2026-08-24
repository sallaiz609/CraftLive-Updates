package hu.craftlive.android;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BedrockMobCatalogTest {
    @Test
    public void catalogContainsCurrentBedrockMobsWithoutDuplicateIds() {
        Set<String> ids = new HashSet<>();
        for (BedrockMobCatalog.Item item : BedrockMobCatalog.all()) ids.add(item.id);

        assertEquals(BedrockMobCatalog.all().size(), ids.size());
        assertTrue(ids.size() >= 90);
        assertTrue(ids.contains("zombie"));
        assertTrue(ids.contains("warden"));
        assertTrue(ids.contains("copper_golem"));
        assertTrue(ids.contains("happy_ghast"));
    }
}
